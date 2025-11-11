## Principles for evaluating output:
- Simplicity of robot-specific implementation is paramount
- Framework should work well for *common* FTC use cases, including from those used by other FTC robots.
- Overall simplicity is important. Code should be as complex as it needs to be to solve the problem elegantly, but no more complex
- No duplication. Code should be reused as much as possible and there should be no duplication of logic.
- Structured organization. Classes should be named for clarity and the package structure should make purpose clear.
- Limited awareness. Each layer should serve a purpose and its awareness of the system should be limited as much as possible, but enough to do the work.
- Document the code with javadocs. Write comments that explain framework contract / requirements, and assumptions. Tell when and how to use classes.
- Telemetry should aid debugging. Add telemetry instrumentation for classes for easy debugging later.

## Principles to use when generating output
- Create a point of view using best practices
- Play the devils advocate and evaluate how the framework will not work, and resolve issues.
- It is okay to propose new interface and break legacy code as long as we are confident that the result is better.
- When we find a problem, think through whether the problem can be generalized and we solve for a bigger issue than the specifically identified issue alone.