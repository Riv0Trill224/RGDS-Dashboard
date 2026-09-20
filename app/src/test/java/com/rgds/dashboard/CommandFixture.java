package com.rgds.dashboard;

/** Real child process used by RootShellTest, never calls su or changes the host. */
public final class CommandFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "root": System.out.println("uid=0(root) gid=0(root)"); break;
            case "user": System.out.println("uid=10000(u0_a0) gid=10000"); break;
            case "denied": System.err.println("Permission denied"); System.exit(1); break;
            case "sleep": System.out.println("started"); System.out.flush(); Thread.sleep(30000); break;
            case "flood":
                for (int i = 0; i < 20000; i++) {
                    System.out.println("stdout " + i);
                    System.err.println("stderr " + i);
                }
                break;
            default: throw new IllegalArgumentException(args[0]);
        }
    }
}
