package jaipilot

import (
	"fmt"
	"os"
	"os/exec"
	"syscall"
	"unsafe"
)

func shellCommand(root, command string) *exec.Cmd {
	cmd := exec.Command("cmd.exe")
	cmd.Dir = root
	cmd.SysProcAttr = &syscall.SysProcAttr{CmdLine: `cmd.exe /d /s /c "` + command + `"`}
	return cmd
}
func terminate(cmd *exec.Cmd, _ bool) {
	if cmd.Process != nil {
		_ = exec.Command("taskkill.exe", "/PID", fmt.Sprint(cmd.Process.Pid), "/T", "/F").Run()
		_ = cmd.Process.Kill()
	}
}

var kernel32 = syscall.NewLazyDLL("kernel32.dll")
var lockFileEx = kernel32.NewProc("LockFileEx")
var unlockFileEx = kernel32.NewProc("UnlockFileEx")

func lockFile(file *os.File) error {
	overlapped := syscall.Overlapped{}
	result, _, err := lockFileEx.Call(file.Fd(), 3, 0, 1, 0, uintptr(unsafe.Pointer(&overlapped)))
	if result == 0 {
		return err
	}
	return nil
}
func unlockFile(file *os.File) error {
	overlapped := syscall.Overlapped{}
	result, _, err := unlockFileEx.Call(file.Fd(), 0, 1, 0, uintptr(unsafe.Pointer(&overlapped)))
	if result == 0 {
		return err
	}
	return nil
}

func terminationSignals() []os.Signal { return []os.Signal{os.Interrupt} }
