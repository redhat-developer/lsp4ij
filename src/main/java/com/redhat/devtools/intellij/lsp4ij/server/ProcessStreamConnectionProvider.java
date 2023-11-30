package com.redhat.devtools.intellij.lsp4ij.server;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

public abstract class ProcessStreamConnectionProvider implements StreamConnectionProvider {
    private @Nullable
    Process process;
    private List<String> commands;
    private @Nullable
    String workingDir;

    public ProcessStreamConnectionProvider() {
    }

    public ProcessStreamConnectionProvider(List<String> commands) {
        this.commands = commands;
    }

    public ProcessStreamConnectionProvider(List<String> commands, String workingDir) {
        this.commands = commands;
        this.workingDir = workingDir;
    }

    @Override
    public void start() throws CannotStartProcessException {
        if (this.commands == null || this.commands.isEmpty() || this.commands.stream().anyMatch(Objects::isNull)) {
            throw new CannotStartProcessException("Unable to start language server: " + this.toString()); //$NON-NLS-1$
        }
        ProcessBuilder builder = createProcessBuilder();
        try {
            Process p = builder.start();
            this.process = p;
        } catch (IOException e) {
            throw new CannotStartProcessException(e);
        }
    }

    @Override
    public boolean isAlive() {
        return process != null ? process.isAlive() : false;
    }

    @Override
    public void ensureIsAlive() throws CannotStartProcessException {
        // Wait few ms before checking the is alive flag.
        synchronized (this.process) {
            try {
                this.process.wait(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!isAlive()) {
            throw new CannotStartProcessException("Unable to start language server: " + this.toString()); //$NON-NLS-1$
        }
    }

    protected ProcessBuilder createProcessBuilder() {
        ProcessBuilder builder = new ProcessBuilder(getCommands());
        if (getWorkingDirectory() != null) {
            builder.directory(new File(getWorkingDirectory()));
        }
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return builder;
    }

    @Override
    public @Nullable
    InputStream getInputStream() {
        Process p = process;
        return p == null ? null : p.getInputStream();
    }

    @Override
    public @Nullable
    InputStream getErrorStream() {
        Process p = process;
        return p == null ? null : p.getErrorStream();
    }

    @Override
    public @Nullable
    OutputStream getOutputStream() {
        Process p = process;
        return p == null ? null : p.getOutputStream();
    }

    public @Nullable
    Long getPid() {
        final Process p = process;
        return p == null ? null : p.pid();
    }

    @Override
    public void stop() {
        Process p = process;
        if (p != null) {
            p.destroy();
            process = null;
        }
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands;
    }

    protected @Nullable
    String getWorkingDirectory() {
        return workingDir;
    }

    public void setWorkingDirectory(String workingDir) {
        this.workingDir = workingDir;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ProcessStreamConnectionProvider)) {
            return false;
        }
        ProcessStreamConnectionProvider other = (ProcessStreamConnectionProvider) obj;
        return Objects.equals(this.getCommands(), other.getCommands())
                && Objects.equals(this.getWorkingDirectory(), other.getWorkingDirectory());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getCommands(), this.getWorkingDirectory());
    }

    @Override
    public String toString() {
        return "ProcessStreamConnectionProvider [commands=" + this.getCommands() + ", workingDir=" //$NON-NLS-1$//$NON-NLS-2$
                + this.getWorkingDirectory() + "]"; //$NON-NLS-1$
    }

}
