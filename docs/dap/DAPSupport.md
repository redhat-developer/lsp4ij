# DAP support

The current implementation of `LSP4IJ` does not yet fully adhere to the [DAP (Debug Adapter Protocol) specification](https://microsoft.github.io/debug-adapter-protocol//specification.html).
This section provides an overview of the supported DAP features for IntelliJ:

## Events

Current state of [Events](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events) support:

* ❌ [Breakpoint](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Breakpoint).
* ❌ [Capabilities](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Capabilities).
* ❌ [Continued](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Continued).
* ❌ [Exited](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Exited).
* ✅ [Initialized](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Initialized).
* ❌ [Invalidated](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Invalidated).
* ❌ [LoadedSource](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_LoadedSource).
* ❌ [Memory](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Memory).
* ❌ [Module](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Module).
* ✅ [Output](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Output).
* ❌ [Process](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Process).
* ❌ [ProgressEnd](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_ProgressEnd).
* ❌ [ProgressStart](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_ProgressStart).
* ❌ [ProgressUpdate](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_ProgressUpdate).
* ✅ [Stopped](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Stopped).
* ✅ [Terminated](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Terminated).
* ❌ [Thread](https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Thread).
 
## Requests

Current state of [Requests](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests) support:

* ✅ [Attach](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Attach).
* ❌ [BreakpointLocations](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_BreakpointLocations).
* ✅ [Completions](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Completions).
* ✅ [ConfigurationDone](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_ConfigurationDone).
* ✅ [Continue](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Continue).
* ❌ [DataBreakpointInfo](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_DataBreakpointInfo).
* ❌ [Disassemble](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Disassemble). 
* ✅ [Disconnect](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Disconnect).
* ✅ [Evaluate](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Evaluate).
* ❌ [ExceptionInfo](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_ExceptionInfo).
* ❌ [Goto](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Goto).
* ❌ [GotoTargets](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_GotoTargets).
* ✅ [Initialize](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Initialize).
* ✅ [Launch](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Launch). 
* ❌ [LoadedSources](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_LoadedSources). 
* ❌ [Locations](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Locations).
* ❌ [Modules](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Modules).
* ✅ [Next](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Next).
* ❌ [Pause](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Pause).
* ❌ [ReadMemory](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_ReadMemory).
* ❌ [Restart](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Restart).
* ❌ [RestartFrame](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_RestartFrame).
* ❌ [ReverseContinue](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_ReverseContinue).
* ✅ [Scopes](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Scopes).
* ✅ [SetBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetBreakpoints).
* ❌ [SetDataBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetDataBreakpoints).
* ❌ [SetExceptionBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetExceptionBreakpoints).
* ❌ [SetExpression](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetExpression).
* ❌ [SetFunctionBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetFunctionBreakpoints).
* ❌ [SetInstructionBreakpoints](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetInstructionBreakpoints).
* ✅ [SetVariable](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetVariable).
* ❌ [Source](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Source).
* ✅ [StackTrace](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StackTrace).
* ❌ [StepBack](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StepBack).
* ✅ [StepIn](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StepIn).
* ❌ [StepInTargets](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StepInTargets).
* ✅ [StepOut](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_StepOut).
* ✅ [Terminate](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Terminate).
* ❌ [TerminateThreads](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_TerminateThreads).
* ✅ [Threads](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Threads).
* ✅ [Variables](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Variables).
* ❌ [WriteMemory](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_WriteMemory).

## Reverse Requests

Current state of [Reverse Requests](https://microsoft.github.io/debug-adapter-protocol//specification.html#Reverse_Requests) support:

* ❌ [RunInTerminal](https://microsoft.github.io/debug-adapter-protocol//specification.html#Reverse_Requests_RunInTerminal).
* ✅ [StartDebugging](https://microsoft.github.io/debug-adapter-protocol//specification.html#Reverse_Requests_StartDebugging).