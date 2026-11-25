# DAP support

The current implementation of `LSP4IJ` does not yet fully adhere to the [DAP (Debug Adapter Protocol) specification](https://microsoft.github.io/debug-adapter-protocol//specification.html).
This section provides an overview of the supported DAP features for IntelliJ:

## Events

Current state of [Events](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events) support:

* ✅ [Breakpoint](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Breakpoint).
* ✅ [Capabilities](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Capabilities): the capabilities that the debug adapter announces after `initialize` update those of its session. The function and data breakpoints are sent again when their support is turned on or off, the exception breakpoint filters when they change, and the instruction breakpoints once the debug adapter supports disassembling.
* ✅ [Continued](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Continued): when the debug adapter says that the program continued, as after a step typed in the Debug console, the session shows it running: the frames and variables of the stop are cleared, Resume and the steps are disabled, and Pause is enabled. A thread which continues alone (`allThreadsContinued: false`) resumes the session only when the session is suspended at that thread; for another thread, the session stays suspended, the `Threads` tab shows that thread running, and the `Frames` view shows `Frames not available: the thread is running` instead of its frames. In a tree of sessions started with `startDebugging`, the event of one session doesn't resume a stop of another.
* ✅ [Exited](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Exited): the console shows the exit code of the debugged program when the debug adapter's process ends.
* ✅ [Initialized](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Initialized).
* ✅ [Invalidated](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Invalidated).
* ✅ [LoadedSource](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_LoadedSource).
* ❌ [Memory](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Memory).
* ✅ [Module](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Module).
* ✅ [Output](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Output): the console indents the lines of a `group` by its nesting level, as VS Code shows the output of `console.group()`. The output of `start` and `startCollapsed` is the header of the group, at the level where the group starts, and the output of `end` is at the outer level. In a tree of sessions started with `startDebugging`, each session has its own groups. A group can't be folded yet, so a `startCollapsed` group is shown expanded. The output of the category `telemetry`, which DAP sends to telemetry instead of the client, isn't shown: LSP4IJ logs it at DEBUG. With the `supportsANSIStyling` client capability, it shows the ANSI styles of the output, such as colours and bold, instead of their escape sequences, as a run console does. Text without an ANSI style keeps the colour of its category, such as red for `stderr`, and a style continues in the next event of the same category.
* ✅ [Process](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Process): the console shows which process the debug adapter debugs, with its PID and how it was started when the event gives them, such as `Debugging process '/path/app' (PID 1234, launched)`; the line of a child session started with `startDebugging` names that session (see [user guide](./UserGuide.md#debugged-process)).
* ❌ [ProgressEnd](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_ProgressEnd).
* ❌ [ProgressStart](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_ProgressStart).
* ❌ [ProgressUpdate](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_ProgressUpdate).
* ✅ [Stopped](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Stopped): the `Threads` tab shows the thread which stopped as stopped, or all threads with `allThreadsStopped`.
* ✅ [Terminated](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Terminated).
* ✅ [Thread](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Thread): the `Threads` tab shows a thread which exited as `exited`, and a thread which started as running.
 
## Requests

Current state of [Requests](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests) support:

* ✅ [Attach](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Attach).
* ❌ [BreakpointLocations](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_BreakpointLocations).
* ❌ [Cancel](https://microsoft.github.io/debug-adapter-protocol//specification.html#Base_Protocol_Cancel).
* ✅ [Completions](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Completions) (see [user guide](./UserGuide.md#completion)).
* ✅ [ConfigurationDone](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_ConfigurationDone).
* ✅ [Continue](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Continue).
* ✅ [DataBreakpointInfo](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_DataBreakpointInfo) (see [user guide](./UserGuide.md#data-breakpoints)).
* ✅ [Disassemble](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Disassemble) (see [user guide](./UserGuide.md#disassemble)).
* ✅ [Disconnect](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Disconnect).
* ✅ [Evaluate](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Evaluate) (see [user guide](./UserGuide.md#evaluate-expression)).
* ✅ [ExceptionInfo](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_ExceptionInfo).
* ❌ [Goto](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Goto).
* ❌ [GotoTargets](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_GotoTargets).
* ✅ [Initialize](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Initialize).
* ✅ [Launch](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Launch). 
* ✅ [LoadedSources](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_LoadedSources).
* ✅ [Locations](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Locations), for the `valueLocationReference` of a variable or an evaluated expression, which `Jump to Value Source` opens, and for the `declarationLocationReference` of a variable, which `Jump to Source` opens (see [user guide](./UserGuide.md#jump-to-value-source)).
* ✅ [Modules](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Modules).
* ✅ [Next](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Next).
* ✅ [Pause](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Pause).
* ❌ [ReadMemory](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_ReadMemory).
* ❌ [Restart](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Restart).
* ✅ [RestartFrame](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_RestartFrame), with the `supportsRestartFrame` capability and the `canRestart` property of a stack frame (see [user guide](./UserGuide.md#reset-frame)).
* ✅ [ReverseContinue](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_ReverseContinue), with the `supportsStepBack` capability (see [user guide](./UserGuide.md#step-back-and-reverse-continue)).
* ✅ [Scopes](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Scopes).
* ✅ [SetBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetBreakpoints) (see [user guide](./UserGuide.md#adding-breakpoint)).
* ✅ [SetDataBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetDataBreakpoints), with the `supportsDataBreakpoints` capability and the [DataBreakpoint](https://microsoft.github.io/debug-adapter-protocol//specification.html#Types_DataBreakpoint) type (see [user guide](./UserGuide.md#data-breakpoints)).
* ✅ [SetExceptionBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetExceptionBreakpoints) (see [user guide](./UserGuide.md#exception-breakpoint))
* ✅ [SetExpression](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetExpression), with the `supportsSetExpression` capability (see [user guide](./UserGuide.md#set-value)).
* ✅ [SetFunctionBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetFunctionBreakpoints), with the `supportsFunctionBreakpoints` capability and the [FunctionBreakpoint](https://microsoft.github.io/debug-adapter-protocol//specification.html#Types_FunctionBreakpoint) type (see [user guide](./UserGuide.md#function-breakpoints)).
* ✅ [SetInstructionBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetInstructionBreakpoints) (see [user guide](./UserGuide.md#disassemble)).
* ✅ [SetVariable](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetVariable).
* ✅ [Source](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Source).
* ✅ [StackTrace](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StackTrace).
* ✅ [StepBack](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StepBack), with the `supportsStepBack` capability (see [user guide](./UserGuide.md#step-back-and-reverse-continue)).
* ✅ [StepIn](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StepIn).
* ✅ [StepInTargets](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StepInTargets).
* ✅ [StepOut](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StepOut).
* ✅ [Terminate](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Terminate).
* ✅ [TerminateThreads](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_TerminateThreads), with the `supportsTerminateThreadsRequest` capability: `Terminate Thread` in the context menu of the `Threads` tab terminates the selected threads, in the DAP session they belong to (see [user guide](./UserGuide.md#terminate-a-thread)).
* ✅ [Threads](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Threads).
* ✅ [Variables](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Variables).
* ❌ [WriteMemory](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_WriteMemory).

## Reverse Requests

Current state of [Reverse Requests](https://microsoft.github.io/debug-adapter-protocol//specification.html#Reverse_Requests) support:

* ✅ [RunInTerminal](https://microsoft.github.io/debug-adapter-protocol//specification.html#Reverse_Requests_RunInTerminal) (see [user guide](./UserGuide.md#run-in-terminal)).
* ✅ [StartDebugging](https://microsoft.github.io/debug-adapter-protocol//specification.html#Reverse_Requests_StartDebugging).