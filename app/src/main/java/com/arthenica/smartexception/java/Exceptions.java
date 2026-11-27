package com.arthenica.smartexception.java;

/**
 * Minimal stand-in for the optional {@code smartexception} dependency, which is not
 * published on Maven Central and is not bundled by ffmpeg-kit. FFmpegKit only calls
 * {@link #getStackTraceString(Throwable)} to format failure output; without this class
 * every FFmpeg failure path throws NoClassDefFoundError and masks the real error.
 */
public final class Exceptions {
    private Exceptions() {
    }

    /** FFmpegKit calls this from its static initializer; a no-op is sufficient. */
    public static void registerRootPackage(String rootPackage) {
    }

    public static String getStackTraceString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }
}