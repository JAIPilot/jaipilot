package jaipilot

import (
	"context"
	"encoding/xml"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type mavenCoverageModel struct {
	Modules   []string `xml:"modules>module"`
	Reporting struct {
		OutputDirectory string `xml:"outputDirectory"`
	} `xml:"reporting"`
	Build struct {
		Directory           string                `xml:"directory"`
		TestSourceDirectory string                `xml:"testSourceDirectory"`
		Plugins             []mavenCoveragePlugin `xml:"plugins>plugin"`
	} `xml:"build"`
}
type mavenCoveragePlugin struct {
	Group         string              `xml:"groupId"`
	Artifact      string              `xml:"artifactId"`
	Configuration mavenCoverageConfig `xml:"configuration"`
	Executions    []struct {
		ID            string              `xml:"id"`
		Goals         []string            `xml:"goals>goal"`
		Configuration mavenCoverageConfig `xml:"configuration"`
	} `xml:"executions>execution"`
}
type mavenCoverageConfig struct {
	Skip            string `xml:"skip"`
	OutputDirectory string `xml:"outputDirectory"`
	DestFile        string `xml:"destFile"`
	DataFile        string `xml:"dataFile"`
	Rules           []struct {
		Element  string   `xml:"element"`
		Includes []string `xml:"includes>include"`
		Excludes []string `xml:"excludes>exclude"`
		Limits   []struct {
			Counter string `xml:"counter"`
			Value   string `xml:"value"`
			Minimum string `xml:"minimum"`
			Maximum string `xml:"maximum"`
		} `xml:"limits>limit"`
	} `xml:"rules>rule"`
}

func shellQuote(value string) string {
	if os.PathSeparator == '\\' {
		return `"` + strings.ReplaceAll(value, `"`, `""`) + `"`
	}
	return "'" + strings.ReplaceAll(value, "'", "'\"'\"'") + "'"
}
func discoverMavenCoverage(ctx context.Context, root string) (*CoveragePolicy, error) {
	if _, err := os.Stat(filepath.Join(root, "pom.xml")); err != nil {
		return nil, errors.New("Automatic CLI coverage discovery currently supports single-module Maven. For Gradle or multi-module builds, use the JAIPilot build plugin or --coverage-policy FILE")
	}
	temp, err := os.CreateTemp("", "jaipilot-effective-pom-*.xml")
	if err != nil {
		return nil, err
	}
	path := temp.Name()
	temp.Close()
	defer os.Remove(path)
	command := "mvn"
	if _, err := os.Stat(filepath.Join(root, "mvnw")); err == nil {
		command = "./mvnw"
	}
	if os.PathSeparator == '\\' {
		command = "mvn.cmd"
		if _, err := os.Stat(filepath.Join(root, "mvnw.cmd")); err == nil {
			command = "mvnw.cmd"
		}
	}
	common := " -Djaipilot.skip=true -Dstyle.color=never"
	r, err := runCommand(ctx, root, object{"command": command + common + " help:effective-pom -Doutput=" + shellQuote(path), "timeoutSeconds": 300, "purpose": "resolve Maven coverage configuration"})
	if err != nil {
		return nil, err
	}
	if r.ExitCode != 0 {
		return nil, fmt.Errorf("Cannot resolve Maven coverage configuration: %s", r.Output)
	}
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var model mavenCoverageModel
	if err := xml.Unmarshal(b, &model); err != nil {
		return nil, err
	}
	if len(model.Modules) > 0 {
		return nil, errors.New("Use the Maven plugin for multi-module coverage")
	}
	p := &CoveragePolicy{Version: 1, Provider: "jacoco", TestRoots: []string{"src/test"}, TestCommand: command + common + " clean test", ReportCommand: command + common + " jacoco:report"}
	found, agent := false, false
	for _, plugin := range model.Build.Plugins {
		if plugin.Group != "org.jacoco" || plugin.Artifact != "jacoco-maven-plugin" {
			continue
		}
		found = true
		if plugin.Configuration.Skip == "true" {
			return nil, errors.New("JaCoCo is disabled")
		}
		dir := model.Build.Directory
		if dir == "" {
			dir = filepath.Join(root, "target")
		}
		reportDir := filepath.Join(dir, "site", "jacoco")
		if model.Reporting.OutputDirectory != "" {
			reportDir = filepath.Join(model.Reporting.OutputDirectory, "jacoco")
		}
		if plugin.Configuration.OutputDirectory != "" {
			reportDir = absolute(root, plugin.Configuration.OutputDirectory)
		}
		dest := filepath.Join(dir, "jacoco.exec")
		if plugin.Configuration.DestFile != "" {
			dest = absolute(root, plugin.Configuration.DestFile)
		}
		reportData := filepath.Join(dir, "jacoco.exec")
		if plugin.Configuration.DataFile != "" {
			reportData = absolute(root, plugin.Configuration.DataFile)
		}
		reportExecutions := 0
		for _, e := range plugin.Executions {
			for _, goal := range e.Goals {
				if goal == "prepare-agent" {
					if e.Configuration.Skip == "true" {
						return nil, errors.New("JaCoCo instrumentation is disabled")
					}
					if e.Configuration.DestFile != "" {
						dest = absolute(root, e.Configuration.DestFile)
					}
				}
				if goal == "report" {
					reportExecutions++
					if reportExecutions > 1 {
						return nil, errors.New("Use a build plugin or explicit policy for multiple JaCoCo reports")
					}
					if e.Configuration.Skip == "true" {
						return nil, errors.New("JaCoCo report is disabled")
					}
					if e.Configuration.OutputDirectory != "" {
						reportDir = absolute(root, e.Configuration.OutputDirectory)
					}
					if e.Configuration.DataFile != "" {
						reportData = absolute(root, e.Configuration.DataFile)
					}
					p.ReportCommand = command + common + " jacoco:report@" + e.ID
				}
			}
		}
		if filepath.Clean(dest) != filepath.Clean(reportData) {
			return nil, errors.New("JaCoCo agent and report data paths differ; align them before generation")
		}
		if model.Build.TestSourceDirectory != "" {
			tests, err := filepath.Rel(root, model.Build.TestSourceDirectory)
			if err != nil {
				return nil, err
			}
			p.TestRoots = []string{filepath.ToSlash(tests)}
		}
		report, err := filepath.Rel(root, filepath.Join(reportDir, "jacoco.xml"))
		if err != nil {
			return nil, err
		}
		report = filepath.ToSlash(report)
		p.Reports = []string{report}
		data, err := filepath.Rel(root, dest)
		if err != nil {
			return nil, err
		}
		p.ExecutionData = []string{filepath.ToSlash(data)}
		for _, execution := range plugin.Executions {
			for _, goal := range execution.Goals {
				if goal == "prepare-agent" {
					agent = true
				}
				if goal != "check" {
					continue
				}
				p.CheckCommand = command + common + " verify"
				config := execution.Configuration
				if len(config.Rules) == 0 {
					config = plugin.Configuration
				}
				for _, rule := range config.Rules {
					element := rule.Element
					if element == "" {
						element = "BUNDLE"
					}
					for _, limit := range rule.Limits {
						counter := limit.Counter
						if counter == "" {
							counter = "INSTRUCTION"
						}
						value := limit.Value
						if value == "" {
							value = "COVEREDRATIO"
						}
						if value != "COVEREDRATIO" || limit.Minimum == "" || limit.Maximum != "" {
							return nil, errors.New("Unsupported automatic JaCoCo limit; use a build plugin with explicit targets and retain the native verification gate")
						}
						raw := strings.TrimSuffix(strings.TrimSpace(limit.Minimum), "%")
						minimum, err := percentage(raw)
						if err != nil {
							return nil, err
						}
						if !strings.HasSuffix(strings.TrimSpace(limit.Minimum), "%") {
							minimum *= 100
						}
						p.Targets = append(p.Targets, CoverageTarget{counter, minimum, element, report, rule.Includes, rule.Excludes})
					}
				}
			}
		}
	}
	if !found || !agent {
		return nil, errors.New("JaCoCo must be configured and active; explicitly set up coverage first")
	}
	return p, nil
}
