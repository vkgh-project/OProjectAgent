        // A usable OpenGL context must actually be current BEFORE the first GL call
        // (glViewport in updateSize below). On systems without working hardware OpenGL
        // (a headless session, an RDP session, or a virtualized GPU), GLFW can still
        // return a non-zero window handle, and the first GL call then triggers a native
        // fatal abort inside lwjgl.dll ("No context is current") that Java cannot catch.
        // Detect that condition here and fail with a clear, actionable message instead.
        long current = glfwGetCurrentContext();
        int clientApi = glfwGetWindowAttrib(handle, GLFW_CLIENT_API);
        if (current == 0L || clientApi != 3 /* GLFW_OPENGL_API */) {
            throw new IllegalStateException(
                    "A usable OpenGL context could not be established "
                    + "(current=" + current + ", clientApi=" + clientApi
                    + ", expected 3=OpenGL). This system has no working hardware OpenGL "
                    + "-- e.g. a headless session, an RDP session, or a virtualized GPU. "
                    + "Windowed mode needs a real GPU with an OpenGL 3.3 core context. "
                    + "On this machine, run headless instead (omit --windowed), or run "
                    + "windowed on a machine with a working GPU driver.");
        }

        glfwSwapInterval(1);
