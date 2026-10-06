import com.jaipilot.build.NativeExecutable;

/** Runs against actual release assets on each supported release installation platform. */
public final class NativeBootstrapCheck {
  public static void main(String[] args) throws Exception {
    String executable = NativeExecutable.resolve("jaipilot");
    if (executable.equals("jaipilot")) {
      throw new IllegalStateException("Bootstrap check requires a PATH without a matching JAIPilot");
    }
    if (!executable.equals(NativeExecutable.resolve("jaipilot"))) {
      throw new IllegalStateException("Native cache was not reused");
    }
    System.out.println("Verified native adapter bootstrap and cache for " + NativeExecutable.VERSION);
  }
}
