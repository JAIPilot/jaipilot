package jaipilot

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"strings"
)

const help = `JAIPilot CLI — high-quality Java tests with measured coverage

Usage:
  jaipilot auth login|status|logout
  jaipilot workflows
  jaipilot update [--check]
  jaipilot mcp [--repo DIR]
  jaipilot acp
  jaipilot coverage check|run --policy FILE [--repo DIR] [--json]
  jaipilot run <workflow> [--repo DIR] (--all | --path PATH... | --class CLASS... | --selection FILE:START-END...) [--json]

Examples:
  jaipilot run improve_coverage --all
  jaipilot run improve_coverage --all --coverage-target 80
  jaipilot run stabilize_flaky_tests --path src/test/java/com/acme/OrderTest.java
  jaipilot run generate_tests --class com.acme.OrderService
  jaipilot run improve_coverage --selection src/main/java/com/acme/OrderService.java:42-88

Commands run locally using your Git, JDK, and build tools. Your JAIPilot account and
credits apply. Selected source, project context, and command output may be sent to
the managed testing service. Review the complete diff afterward.`

type runOptions struct {
	repo           string
	scope          ScopeInput
	json           bool
	coveragePolicy string
	coverageTarget *float64
	branchTarget   *float64
}

func parseRun(args []string) (runOptions, error) {
	options := runOptions{repo: cwd()}
	for i := 0; i < len(args); i++ {
		switch arg := args[i]; arg {
		case "--all":
			options.scope.All = true
		case "--json":
			options.json = true
		case "--repo", "--path", "--class", "--selection", "--coverage-policy", "--policy", "--coverage-target", "--branch-coverage-target":
			i++
			if i == len(args) || args[i] == "" || strings.HasPrefix(args[i], "--") {
				return options, fmt.Errorf("Missing value for %s", arg)
			}
			switch arg {
			case "--coverage-policy", "--policy":
				options.coveragePolicy = args[i]
			case "--coverage-target", "--branch-coverage-target":
				n, err := percentage(args[i])
				if err != nil {
					return options, err
				}
				if arg == "--coverage-target" {
					options.coverageTarget = &n
				} else {
					options.branchTarget = &n
				}
			case "--repo":
				options.repo = args[i]
			case "--path":
				options.scope.Paths = append(options.scope.Paths, args[i])
			case "--class":
				options.scope.Classes = append(options.scope.Classes, args[i])
			case "--selection":
				options.scope.Selections = append(options.scope.Selections, args[i])
			}
		default:
			return options, fmt.Errorf("Unknown option: %s", arg)
		}
	}
	return options, nil
}

func mainCommand(ctx context.Context, args []string) (int, error) {
	if len(args) == 0 {
		fmt.Println(help)
		return 0, nil
	}
	switch args[0] {
	case "--version", "version":
		fmt.Println("JAIPilot CLI " + Version)
		return 0, nil
	case "help", "--help", "-h":
		fmt.Println(help)
		return 0, nil
	case "auth":
		if len(args) != 2 {
			return 1, errors.New("Use `jaipilot auth login|status|logout`")
		}
		switch args[1] {
		case "login":
			email, err := login(ctx)
			if err != nil {
				return 1, err
			}
			fmt.Println("Signed in as " + email)
		case "logout":
			if err := logout(); err != nil {
				return 1, err
			}
			fmt.Println("Signed out")
		case "status":
			s, err := readSession()
			if err != nil {
				return 1, err
			}
			if s == nil {
				fmt.Println("Not signed in")
			} else {
				fmt.Println("Signed in as " + s.Email)
			}
		default:
			return 1, errors.New("Use `jaipilot auth login|status|logout`")
		}
		return 0, nil
	case "update":
		if len(args) > 2 || (len(args) == 2 && args[1] != "--check") {
			return 1, errors.New("Use `jaipilot update [--check]`")
		}
		_, err := updateCLI(ctx, nil, updateOptions{checkOnly: len(args) == 2})
		return 0, err
	case "workflows":
		if len(args) != 1 {
			return 1, errors.New("Unexpected workflows arguments")
		}
		items, err := workflows(ctx)
		if err != nil {
			return 1, err
		}
		for _, item := range items {
			fmt.Printf("%-24s %s\n", str(item["id"]), str(item["description"]))
		}
		return 0, nil
	case "coverage":
		if len(args) < 2 || (args[1] != "check" && args[1] != "run") {
			return 1, errors.New("Use jaipilot coverage check|run --policy FILE [--repo DIR] [--json]")
		}
		options, err := parseRun(args[2:])
		if err != nil {
			return 1, err
		}
		root, err := repositoryRoot(ctx, options.repo)
		if err != nil {
			return 1, err
		}
		if options.coveragePolicy == "" {
			return 1, errors.New("Coverage requires --coverage-policy FILE (or --policy FILE)")
		}
		p, err := loadCoveragePolicy(root, options.coveragePolicy)
		if err != nil {
			return 1, err
		}
		if options.scope.All || len(options.scope.Paths)+len(options.scope.Classes)+len(options.scope.Selections) > 0 {
			return 1, errors.New("Coverage scope comes from the policy; use run improve_coverage for scope flags")
		}
		scopeInput := ScopeInput{All: true}
		if len(p.ScopePaths) > 0 {
			scopeInput = ScopeInput{Paths: p.ScopePaths}
		}
		scope, err := resolveScope(ctx, root, scopeInput)
		if err != nil {
			return 1, err
		}
		if options.coverageTarget != nil {
			if err := addCoverageTarget(root, p, "LINE", *options.coverageTarget, scope); err != nil {
				return 1, err
			}
		}
		if options.branchTarget != nil {
			if err := addCoverageTarget(root, p, "BRANCH", *options.branchTarget, scope); err != nil {
				return 1, err
			}
		}
		if err := p.validate(root); err != nil {
			return 1, err
		}
		result, err := runCoverage(ctx, root, "improve_coverage", scope, p, args[1] == "check")
		if err != nil {
			return 1, err
		}
		if options.json {
			if err := json.NewEncoder(os.Stdout).Encode(result); err != nil {
				return 1, err
			}
		} else {
			fmt.Println(coverageText(result))
		}
		if str(result["status"]) != "complete" {
			return 2, nil
		}
		return 0, nil
	case "run":
		if len(args) < 2 || strings.HasPrefix(args[1], "--") {
			return 1, errors.New("Specify a workflow; run `jaipilot workflows`")
		}
		options, err := parseRun(args[2:])
		if err != nil {
			return 1, err
		}
		root, err := repositoryRoot(ctx, options.repo)
		if err != nil {
			return 1, err
		}
		scope, err := resolveScope(ctx, root, options.scope)
		if err != nil {
			return 1, err
		}
		var result object
		if options.coveragePolicy != "" || options.coverageTarget != nil || options.branchTarget != nil {
			if args[1] != "improve_coverage" {
				return 1, errors.New("Coverage policy options require improve_coverage")
			}
			var p *CoveragePolicy
			if options.coveragePolicy != "" {
				p, err = loadCoveragePolicy(root, options.coveragePolicy)
			} else {
				p, err = discoverMavenCoverage(ctx, root)
			}
			if err != nil {
				return 1, err
			}
			if options.coverageTarget != nil {
				if err := addCoverageTarget(root, p, "LINE", *options.coverageTarget, scope); err != nil {
					return 1, err
				}
			}
			if options.branchTarget != nil {
				if err := addCoverageTarget(root, p, "BRANCH", *options.branchTarget, scope); err != nil {
					return 1, err
				}
			}
			if err := p.validate(root); err != nil {
				return 1, err
			}
			result, err = runCoverage(ctx, root, args[1], scope, p, false)
		} else {
			result, err = runWorkflow(ctx, root, args[1], scope, workflowOptions{})
		}
		if err != nil {
			return 1, err
		}
		if options.json {
			encoder := json.NewEncoder(os.Stdout)
			encoder.SetIndent("", "  ")
			if err := encoder.Encode(result); err != nil {
				return 1, err
			}
		} else {
			if result["coveragePolicy"] != nil || result["coverageBefore"] != nil {
				fmt.Println(coverageText(result))
			} else {
				fmt.Println(resultText(result))
			}
		}
		if str(result["status"]) != "complete" {
			return 2, nil
		}
		return 0, nil
	case "mcp":
		root := cwd()
		if len(args) > 1 {
			if len(args) != 3 || args[1] != "--repo" {
				return 1, errors.New("Use jaipilot mcp [--repo DIR]")
			}
			root = args[2]
		}
		root, err := repositoryRoot(ctx, root)
		if err != nil {
			return 1, err
		}
		rpc := newRPC(ctx, os.Stdout)
		jobs := newJobs(rpc.ctx, root)
		defer jobs.stop()
		return 0, serveStdio(ctx, rpc, (&mcpHandler{jobs: jobs}).handle)
	case "acp":
		if len(args) == 2 && args[1] == "--version" {
			fmt.Println("JAIPilot ACP " + Version)
			return 0, nil
		}
		if len(args) == 2 && args[1] == "--login" {
			email, err := login(ctx)
			if err != nil {
				return 1, err
			}
			fmt.Fprintln(os.Stderr, "Signed in as "+email)
			return 0, nil
		}
		if len(args) > 1 && !(len(args) == 2 && args[1] == "acp") {
			return 1, errors.New("Use jaipilot-acp [acp | --login | --version]")
		}
		rpc := newRPC(ctx, os.Stdout)
		agent := newACP(rpc)
		defer agent.stop()
		return 0, serveStdio(ctx, rpc, agent.handle)
	default:
		return 1, fmt.Errorf("Unknown command: %s", args[0])
	}
}

func serveStdio(ctx context.Context, rpc *rpcConnection, handler rpcHandler) error {
	done := make(chan error, 1)
	go func() { done <- rpc.serve(os.Stdin, handler) }()
	select {
	case err := <-done:
		return err
	case <-ctx.Done():
		rpc.cancel()
		return nil
	}
}

func Main(args []string) int {
	ctx, stop := signal.NotifyContext(context.Background(), terminationSignals()...)
	defer stop()
	if shouldAutoUpdate(args) {
		if code, err := updateCLI(ctx, args, updateOptions{automatic: true}); err == nil && code != nil {
			return *code
		}
	}
	code, err := mainCommand(ctx, args)
	if err != nil {
		fmt.Fprintln(os.Stderr, "JAIPilot: "+err.Error())
		return 1
	}
	return code
}
