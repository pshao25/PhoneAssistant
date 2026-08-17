package com.phoneassistant.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import android.view.accessibility.AccessibilityEvent
import com.phoneassistant.app.BuildConfig
import com.phoneassistant.app.guidance.DecisionValidation
import com.phoneassistant.app.guidance.GuidanceDecision
import com.phoneassistant.app.guidance.GuidanceDecisionValidator
import com.phoneassistant.app.guidance.GuidanceJsonEncoder
import com.phoneassistant.app.guidance.GuidanceRequest
import com.phoneassistant.app.guidance.GuidanceStatus
import com.phoneassistant.app.guidance.GuidanceTargetStore
import com.phoneassistant.app.guidance.LocalGuidanceAgent
import com.phoneassistant.app.guidance.RemoteGuidanceAgent
import com.phoneassistant.app.screen.ElementBounds
import com.phoneassistant.app.screen.ScreenSnapshot
import java.util.concurrent.Executors

class PhoneAssistAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private val snapshotBuilder = ScreenSnapshotBuilder()
    private val guidanceExecutor = Executors.newSingleThreadExecutor()
    private val guidanceAgent = RemoteGuidanceAgent(
        endpoint = BuildConfig.GUIDANCE_API_URL,
        fallback = LocalGuidanceAgent(),
    )
    private val decisionValidator = GuidanceDecisionValidator()
    private var overlayView: HighlightOverlayView? = null
    private var instructionView: InstructionOverlayView? = null
    private var completionView: CompletionOverlayView? = null
    private var highlightedBounds: Rect? = null
    private var activeTargetBounds: Rect? = null
    private var activeDecision: GuidanceDecision? = null
    private var retryMessage: String? = null
    private var lastCompletedDecision: GuidanceDecision? = null
    private var pendingPackageName = ""
    private var lastSnapshot = ""
    private var requestSequence = 0L
    private var taskRevision = Long.MIN_VALUE
    private var taskId = ""
    private var stepNumber = 1
    private val completedSteps = mutableListOf<String>()
    private var taskCompleted = false
    private var taskArmed = false
    private val captureScreen = Runnable {
        val root = rootInActiveWindow ?: return@Runnable
        val snapshot = snapshotBuilder.build(
            root = root,
            packageName = root.packageName?.toString().orEmpty(),
        )
        val snapshotSignature = snapshot.elements.joinToString("|") { element ->
            "${element.id}:${element.enabled}:${element.checked}"
        }
        val needsInstructionRestore =
            (activeDecision != null || retryMessage != null) &&
                instructionView?.isAttachedToWindow != true
        if (snapshotSignature == lastSnapshot && !needsInstructionRestore) return@Runnable

        lastSnapshot = snapshotSignature
        updateGuidance(snapshot)
        logSnapshot(snapshot)
    }

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startPendingTask()
        Log.i(TAG, "PhoneAssist accessibility service connected")
    }

    private fun startPendingTask() {
        val revision = GuidanceTargetStore.getRevision(this)
        if (taskArmed && taskRevision == revision) return
        if (!GuidanceTargetStore.isStarted(this, revision)) return
        resetTask(GuidanceTargetStore.get(this), revision)
        taskArmed = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        pendingPackageName = event?.packageName?.toString().orEmpty()
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            dismissClickedHighlight(event)
        }
        handler.removeCallbacks(captureScreen)
        handler.postDelayed(captureScreen, CAPTURE_DEBOUNCE_MS)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(captureScreen)
        requestSequence++
        activeDecision = null
        removeHighlight()
        removeInstruction()
        removeCompletion()
        Log.w(TAG, "PhoneAssist accessibility service interrupted")
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureScreen)
        requestSequence++
        guidanceExecutor.shutdownNow()
        activeDecision = null
        removeHighlight()
        removeInstruction()
        removeCompletion()
        super.onDestroy()
    }

    private fun updateGuidance(snapshot: ScreenSnapshot) {
        startPendingTask()
        val userGoal = GuidanceTargetStore.get(this)
        val currentRevision = GuidanceTargetStore.getRevision(this)
        if (currentRevision != taskRevision) {
            resetTask(userGoal, currentRevision)
        }
        Log.d(
            TAG,
            "guidance_state package=${snapshot.packageName} armed=$taskArmed " +
                "completed=$taskCompleted decision=${activeDecision?.status} retry=${retryMessage != null}",
        )
        if (!taskArmed) {
            requestSequence++
            activeDecision = null
            removeHighlight()
            removeInstruction()
            removeCompletion()
            return
        }
        if (userGoal.isBlank()) {
            requestSequence++
            activeDecision = null
            removeHighlight()
            removeInstruction()
            removeCompletion()
            return
        }
        if (snapshot.packageName == packageName) {
            removeHighlight()
            removeCompletion()
            return
        }
        if (taskCompleted) return
        retryMessage?.let { message ->
            if (instructionView?.isAttachedToWindow != true) showRetryInstruction(message)
            return
        }
        val activeTargetId = activeDecision?.targetElementId
        if (
            activeTargetId != null &&
            snapshot.elements.none { element -> element.id == activeTargetId }
        ) {
            activeTargetBounds = null
            removeHighlight()
        }
        activeDecision?.let { decision ->
            if (instructionView?.isAttachedToWindow != true) {
                showInstruction(
                    message = "Step $stepNumber: ${decision.instruction}",
                    canConfirm = true,
                )
            }
            return
        }
        if (stepNumber > MAX_STEPS) {
            requestSequence++
            removeHighlight()
            showInstruction(
                message = "Stopped after $MAX_STEPS steps. Return to PhoneAssist and try a more specific goal.",
            )
            return
        }

        val request = GuidanceRequest(
            userGoal = userGoal,
            screen = snapshot,
            previousStep = lastCompletedDecision,
            taskId = taskId,
            stepNumber = stepNumber,
            completedSteps = completedSteps.toList(),
        )
        val currentRequest = ++requestSequence
        guidanceExecutor.execute {
            val decision = guidanceAgent.decide(request)
            handler.post {
                if (currentRequest == requestSequence) {
                    applyDecision(request, snapshot, decision)
                }
            }
        }
    }

    private fun applyDecision(
        request: GuidanceRequest,
        snapshot: ScreenSnapshot,
        decision: GuidanceDecision,
    ) {
        when (val validation = decisionValidator.validate(request, decision)) {
            is DecisionValidation.Invalid -> {
                activeDecision = null
                removeHighlight()
                showRetryInstruction(ERROR_RETURN_MESSAGE)
                Log.w(TAG, "decision rejected reason=${validation.reason} decision=$decision")
            }
            is DecisionValidation.Valid -> {
                if (
                    validation.decision.status == GuidanceStatus.COMPLETE &&
                    !looksLikePendingAction(validation.decision.instruction)
                ) {
                    completeGuidance(validation.decision.instruction)
                    return
                }
                if (validation.decision.status != GuidanceStatus.CONTINUE) {
                    activeDecision = null
                    removeHighlight()
                    showRetryInstruction(
                        "${validation.decision.instruction} Tap Back, then Retry.",
                    )
                    Log.w(TAG, "guidance cannot continue status=${validation.decision.status}")
                    return
                }
                val target = snapshot.elements.find {
                    it.id == validation.decision.targetElementId
                }
                if (target == null) {
                    activeDecision = null
                    removeHighlight()
                    showRetryInstruction(ERROR_RETURN_MESSAGE)
                } else {
                    activeDecision = validation.decision
                    activeTargetBounds = target.bounds.toRect()
                    removeCompletion()
                    showHighlight(target.bounds)
                    showInstruction(
                        message = "Step ${request.stepNumber}: ${validation.decision.instruction}",
                        targetBounds = target.bounds,
                    )
                }
                Log.i(TAG, "guidance_request=${GuidanceJsonEncoder.encodeRequest(request)}")
                Log.i(
                    TAG,
                    "guidance_decision=${GuidanceJsonEncoder.encodeDecision(validation.decision)}",
                )
            }
        }
    }

    private fun confirmCurrentStep() {
        val decision = activeDecision ?: return
        completedSteps += decision.instruction
        stepNumber++
        lastCompletedDecision = decision
        activeDecision = null
        activeTargetBounds = null
        requestSequence++
        lastSnapshot = ""
        removeHighlight()
        removeInstruction()
        handler.removeCallbacks(captureScreen)
        handler.post(captureScreen)
        Log.i(TAG, "guidance_step_confirmed step=${stepNumber - 1} instruction=${decision.instruction}")
    }

    private fun retryGuidance() {
        retryMessage = null
        requestSequence++
        lastSnapshot = ""
        removeInstruction()
        handler.removeCallbacks(captureScreen)
        handler.post(captureScreen)
        Log.i(TAG, "guidance_retry step=$stepNumber")
    }

    private fun exitGuidance() {
        requestSequence++
        taskCompleted = true
        taskArmed = false
        GuidanceTargetStore.markStopped(this, taskRevision)
        activeDecision = null
        retryMessage = null
        lastCompletedDecision = null
        activeTargetBounds = null
        removeHighlight()
        removeInstruction()
        removeCompletion()
        Log.i(TAG, "guidance_exited id=$taskId")
    }

    private fun dismissClickedHighlight(event: AccessibilityEvent) {
        val targetBounds = activeTargetBounds ?: return
        if (event.packageName?.toString() == packageName) return

        var clickedNode = event.source
        var matchesTarget = clickedNode == null
        while (clickedNode != null && !matchesTarget) {
            val clickedBounds = Rect()
            clickedNode.getBoundsInScreen(clickedBounds)
            matchesTarget = Rect.intersects(targetBounds, clickedBounds)
            clickedNode = clickedNode.parent
        }
        if (!matchesTarget) return

        activeTargetBounds = null
        removeHighlight()
        Log.i(TAG, "guidance_highlight_dismissed step=$stepNumber")
    }

    private fun resetTask(userGoal: String, revision: Long) {
        requestSequence++
        taskRevision = revision
        taskId = "task_$revision"
        stepNumber = 1
        completedSteps.clear()
        activeDecision = null
        retryMessage = null
        lastCompletedDecision = null
        activeTargetBounds = null
        taskCompleted = GuidanceTargetStore.isComplete(this, revision) ||
            GuidanceTargetStore.isStopped(this, revision)
        lastSnapshot = ""
        Log.i(TAG, "guidance_task_started id=$taskId goal=$userGoal")
    }

    private fun completeGuidance(message: String) {
        requestSequence++
        taskCompleted = true
        GuidanceTargetStore.markComplete(this, taskRevision)
        activeDecision = null
        retryMessage = null
        lastCompletedDecision = null
        activeTargetBounds = null
        removeHighlight()
        removeInstruction()
        showCompletion(message)
        Log.i(TAG, "guidance_complete message=$message")
    }

    private fun showCompletion(message: String) {
        removeCompletion()
        val view = CompletionOverlayView(this).apply { text = message }
        val horizontalMargin = (24 * resources.displayMetrics.density).toInt()
        val displayBounds = currentDisplayBounds()
        val params = WindowManager.LayoutParams(
            displayBounds.width() - horizontalMargin * 2,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (120 * resources.displayMetrics.density).toInt()
        }
        if (!addOverlay(view, params)) return
        completionView = view
        handler.postDelayed({ removeCompletion() }, COMPLETION_DURATION_MS)
    }

    private fun removeCompletion() {
        completionView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        completionView = null
    }

    private fun showInstruction(
        message: String,
        targetBounds: ElementBounds? = null,
        canConfirm: Boolean = true,
        actionLabel: String = "Confirm",
        onAction: () -> Unit = ::confirmCurrentStep,
    ) {
        removeInstruction()
        val view = InstructionOverlayView(
            context = this,
            showConfirm = canConfirm,
            confirmLabel = actionLabel,
            onConfirm = onAction,
            onExit = ::exitGuidance,
        ).apply {
            instruction = message
        }
        val density = resources.displayMetrics.density
        val horizontalMargin = (12 * density).toInt()
        val displayBounds = currentDisplayBounds()
        val bannerWidth = minOf(
            displayBounds.width() - horizontalMargin * 2,
            (320 * density).toInt(),
        )
        val showAtTop = targetBounds == null ||
            (targetBounds.top + targetBounds.bottom) / 2 > displayBounds.height() / 2
        val params = WindowManager.LayoutParams(
            bannerWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (showAtTop) Gravity.TOP else Gravity.BOTTOM) or
                Gravity.CENTER_HORIZONTAL
            y = ((if (showAtTop) 56 else 36) * density).toInt()
        }
        if (addOverlay(view, params)) instructionView = view
    }

    private fun showRetryInstruction(message: String) {
        retryMessage = message
        showInstruction(
            message = message,
            actionLabel = "Retry",
            onAction = ::retryGuidance,
        )
    }

    private fun looksLikePendingAction(instruction: String): Boolean {
        val normalized = instruction.trim().lowercase()
        return PENDING_ACTION_PREFIXES.any(normalized::startsWith)
    }

    private fun removeInstruction() {
        instructionView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        instructionView = null
    }

    private fun showHighlight(bounds: ElementBounds) {
        val androidBounds = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (highlightedBounds == androidBounds && overlayView != null) return
        removeHighlight()

        val padding = HIGHLIGHT_PADDING_PX
        val displayBounds = currentDisplayBounds()
        val overlayBounds = Rect(
            (androidBounds.left - padding).coerceAtLeast(0),
            (androidBounds.top - padding).coerceAtLeast(0),
            (androidBounds.right + padding).coerceAtMost(displayBounds.width()),
            (androidBounds.bottom + padding).coerceAtMost(displayBounds.height()),
        )
        val view = HighlightOverlayView(this)
        val params = WindowManager.LayoutParams(
            overlayBounds.width(),
            overlayBounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = overlayBounds.left
            y = overlayBounds.top
        }

        if (!addOverlay(view, params)) return
        overlayView = view
        highlightedBounds = androidBounds
    }

    private fun addOverlay(view: android.view.View, params: WindowManager.LayoutParams): Boolean =
        try {
            windowManager.addView(view, params)
            true
        } catch (error: BadTokenException) {
            Log.w(TAG, "Accessibility overlay token unavailable; waiting for next window event", error)
            handler.removeCallbacks(captureScreen)
            handler.postDelayed(captureScreen, OVERLAY_RETRY_DELAY_MS)
            false
        }

    private fun removeHighlight() {
        overlayView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        overlayView = null
        highlightedBounds = null
    }

    private fun ElementBounds.toRect(): Rect = Rect(left, top, right, bottom)

    private fun currentDisplayBounds(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            Rect(
                0,
                0,
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels,
            )
        }

    private fun logSnapshot(snapshot: ScreenSnapshot) {
        val elements = snapshot.elements.joinToString("\n") { element ->
            "id=${element.id} label=${element.label} description=${element.description} " +
                "role=${element.role} clickable=${element.clickable} enabled=${element.enabled} " +
                "checked=${element.checked} bounds=${element.bounds}"
        }
        Log.d(
            TAG,
            "snapshot package=${snapshot.packageName} title=${snapshot.screenTitle} " +
                "elements=${snapshot.elements.size}\n$elements",
        )
    }

    private companion object {
        const val TAG = "PhoneAssistAccessibility"
        const val CAPTURE_DEBOUNCE_MS = 750L
        const val OVERLAY_RETRY_DELAY_MS = 500L
        const val HIGHLIGHT_PADDING_PX = 8
        const val COMPLETION_DURATION_MS = 3_000L
        const val MAX_STEPS = 8
        const val ERROR_RETURN_MESSAGE = "Error: Tap Back to return, then tap Retry."
        val PENDING_ACTION_PREFIXES = listOf(
            "tap ", "click ", "press ", "open ", "select ",
            "点击", "点按", "打开", "选择", "返回",
        )
    }
}