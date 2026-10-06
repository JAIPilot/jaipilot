package jaipilot

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const site = "https://www.jaipilot.com"

type Session struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresAt    int64  `json:"expires_at"`
	Email        string `json:"email"`
}

func sessionPath() string {
	base := os.Getenv("JAIPILOT_CONFIG_DIR")
	if base == "" {
		if runtime.GOOS == "windows" {
			base = os.Getenv("APPDATA")
		} else {
			base = os.Getenv("XDG_CONFIG_HOME")
		}
	}
	if base == "" {
		home, _ := os.UserHomeDir()
		base = filepath.Join(home, ".config")
	}
	return filepath.Join(base, "jaipilot", "session.json")
}
func readSession() (*Session, error) {
	b, err := os.ReadFile(sessionPath())
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, errors.New("JAIPilot session could not be read")
	}
	var s Session
	if json.Unmarshal(b, &s) != nil {
		return nil, errors.New("JAIPilot session could not be read")
	}
	if s.AccessToken == "" || s.RefreshToken == "" || s.Email == "" {
		return nil, nil
	}
	return &s, nil
}
func logout() error {
	err := os.Remove(sessionPath())
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}
func openBrowser(ctx context.Context, address string) {
	name := "xdg-open"
	args := []string{address}
	if runtime.GOOS == "darwin" {
		name = "open"
	}
	if runtime.GOOS == "windows" {
		name = "cmd.exe"
		args = []string{"/c", "start", "", address}
	}
	cmd := exec.CommandContext(ctx, name, args...)
	if cmd.Run() != nil {
		fmt.Fprintln(os.Stderr, "Open the sign-in URL above in a browser on this computer.")
	}
}
func login(ctx context.Context) (string, error) {
	ctx, cancel := context.WithTimeout(ctx, 3*time.Minute)
	defer cancel()
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		return "", err
	}
	state := uuid()
	completed := make(chan Session, 1)
	server := &http.Server{ReadHeaderTimeout: 10 * time.Second, Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		if r.Method != "GET" || r.URL.Path != "/auth/callback" || q.Get("state") != state {
			http.Error(w, "Invalid callback", 400)
			return
		}
		var expiry int64
		_, err := fmt.Sscan(q.Get("expires_at"), &expiry)
		s := Session{q.Get("access_token"), q.Get("refresh_token"), expiry, q.Get("email")}
		if err != nil || s.AccessToken == "" || s.RefreshToken == "" || s.Email == "" {
			http.Error(w, "Invalid session", 400)
			return
		}
		w.Header().Set("Content-Type", "text/plain")
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Referrer-Policy", "no-referrer")
		fmt.Fprint(w, "JAIPilot sign-in complete. You can close this tab.")
		select {
		case completed <- s:
		default:
		}
	})}
	go func() { _ = server.Serve(listener) }()
	defer func() {
		closeCtx, closeCancel := context.WithTimeout(context.Background(), time.Second)
		defer closeCancel()
		_ = server.Shutdown(closeCtx)
		_ = server.Close()
	}()
	u, _ := url.Parse(site + "/plugin-login")
	q := u.Query()
	q.Set("redirect_uri", "http://"+listener.Addr().String()+"/auth/callback")
	q.Set("state", state)
	u.RawQuery = q.Encode()
	fmt.Fprintln(os.Stderr, "Complete JAIPilot sign-in:", u.String())
	go openBrowser(ctx, u.String())
	select {
	case s := <-completed:
		if err := atomicJSON(sessionPath(), s, 0600); err != nil {
			return "", err
		}
		return s.Email, nil
	case <-ctx.Done():
		return "", fmt.Errorf("Sign-in cancelled or timed out: %w", ctx.Err())
	}
}
func bearerToken(ctx context.Context) (string, error) {
	s, err := readSession()
	if err != nil {
		return "", err
	}
	if s == nil {
		return "", errors.New("Sign in first with `jaipilot auth login`")
	}
	if s.ExpiresAt > time.Now().Unix()+60 {
		return s.AccessToken, nil
	}
	ctx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, "POST", site+"/plugin-refresh", strings.NewReader(jsonText(object{"refresh_token": s.RefreshToken})))
	if err != nil {
		return "", err
	}
	request.Header.Set("Content-Type", "application/json")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		return "", errors.New("Could not refresh JAIPilot sign-in; check your connection")
	}
	defer response.Body.Close()
	if response.StatusCode == 401 || response.StatusCode == 403 {
		_ = logout()
		return "", errors.New("JAIPilot sign-in expired; run `jaipilot auth login`")
	}
	if response.StatusCode != 200 {
		return "", fmt.Errorf("Could not refresh JAIPilot sign-in (HTTP %d)", response.StatusCode)
	}
	var v Session
	if err := json.NewDecoder(http.MaxBytesReader(nil, response.Body, 1<<20)).Decode(&v); err != nil || v.AccessToken == "" {
		return "", errors.New("JAIPilot returned an invalid session")
	}
	if v.RefreshToken == "" {
		v.RefreshToken = s.RefreshToken
	}
	if v.Email == "" {
		v.Email = s.Email
	}
	if err := atomicJSON(sessionPath(), v, 0600); err != nil {
		return "", err
	}
	return v.AccessToken, nil
}
