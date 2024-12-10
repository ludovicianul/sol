package io.ludovicianul.service;

import java.util.List;

/** Service class that provides methods to determine the type of a file based on its path. */
public class FileTypeService {
  public static final String DOT_FILES = ".*(^|/|\\.)\\.[^/]+.*$";

  public static final List<String> DOCUMENTATION_FILES =
      List.of(
          ".*README(\\.md|\\.txt|\\.rst|\\.adoc)?$", // Readme files
          ".*CHANGELOG(\\.md|\\.txt|\\.rst)?$", // Changelog files
          ".*LICENSE(\\.md|\\.txt)?$", // License files
          ".*COPYING(\\.md|\\.txt)?$", // Alternative license files
          ".*CONTRIBUTING(\\.md|\\.txt|\\.rst)?$", // Contributing guidelines
          ".*CODE_OF_CONDUCT(\\.md|\\.txt|\\.rst)?$", // Code of Conduct files
          ".*INSTALL(\\.md|\\.txt|\\.rst)?$", // Installation instructions
          ".*UPGRADE(\\.md|\\.txt|\\.rst)?$", // Upgrade guides
          ".*SECURITY(\\.md|\\.txt)?$", // Security guidelines
          ".*AUTHORS(\\.md|\\.txt)?$", // Author list
          ".*FAQ(\\.md|\\.txt|\\.rst)?$", // FAQ files
          ".*ARCHITECTURE(\\.md|\\.txt|\\.rst)?$", // Architecture documents
          ".*docs/.*\\.(md|txt|rst|html|pdf)$", // General documentation folder
          ".*\\.rst$", // reStructuredText files
          ".*\\.adoc$", // AsciiDoc files
          ".*\\.pdf$" // PDF documentation
          );
  public static final List<String> TEST_FILES =
      List.of(
          ".*Test\\.(java|kt|py|js|ts|cpp|c|rb|php|go|rs|cs)$", // Common test file naming convention
          ".*_test\\.(py|go|js|ts|c|cpp|rs|cs)$", // Underscore test naming convention
          ".*\\.spec\\.(js|ts|py)$", // Spec naming for JavaScript/TypeScript/Python
          ".*\\.(test|spec)\\.(js|ts|jsx|tsx)$", // Extended JS/TS test patterns
          ".*\\.feature$", // Cucumber feature files
          ".*\\.t\\.(pl|rb)$", // Perl/Ruby test files
          ".*\\.ut\\.(cpp|c)$", // Unit test files for C/C++
          ".*_tests\\.rs$", // Rust test files
          ".*\\.test\\.(rs|go)$", // Rust/Golang test files

          // Test files located in src/.../test directories
          ".*/src/.*/test/.*/.*\\.(java|kt|py|js|ts|cpp|c|rb|php|go|rs|cs)$",
          ".*test/.*/.*\\.(java|kt|py|js|ts|cpp|c|rb|php|go|rs|cs)$",
          ".*/test/.*/.*_test\\.(py|go|cpp|rs)$",
          ".*/src/.*/test/.*/.*\\.spec\\.(js|ts)$",
          ".*/src/.*/test/.*/.*\\.(test|spec)\\.(js|ts|jsx|tsx)$",
          ".*/src/.*/test/.*/.*\\.feature$",
          ".*/src/.*/test/.*/.*\\.t\\.(pl|rb)$");

  public static final List<String> BUILD_FILES =
      List.of(
          // Build configuration files
          ".*pom\\.xml$", // Maven
          ".*build\\.gradle$", // Gradle
          ".*build\\.gradle\\.kts$", // Kotlin DSL for Gradle
          ".*Makefile$", // Make
          ".*CMakeLists\\.txt$", // CMake
          ".*package\\.json$", // Node.js (npm)
          ".*yarn\\.lock$", // Yarn
          ".*setup\\.py$", // Python setuptools
          ".*lock.json$", // Node.js (lockfile)
          ".*lock.yml$", // Node.js (lockfile)
          ".*lock.yaml", // Node.js (lockfile)
          ".*requirements\\.txt$", // Python dependencies
          ".*composer\\.json$", // PHP Composer
          ".*build\\.sbt$", // Scala SBT
          ".*Rakefile$", // Ruby Rake
          ".*Dockerfile$", // Docker
          ".*docker-compose\\.yml$", // Docker Compose
          ".*Gemfile$", // Ruby Gem
          ".*\\.csproj$", // .NET Core project files
          ".*Cargo\\.toml$", // Rust Cargo
          ".*go\\.mod$", // Golang modules

          // CI/CD configuration files
          ".*Jenkinsfile$", // Jenkins
          ".*\\.github/workflows/.*\\.yml$", // GitHub Actions workflows
          ".*\\.gitlab-ci\\.yml$", // GitLab CI
          ".*\\.travis\\.yml$", // Travis CI
          ".*\\.circleci/config\\.yml$", // CircleCI
          ".*azure-pipelines\\.yml$", // Azure Pipelines
          ".*bitbucket-pipelines\\.yml$", // Bitbucket Pipelines
          ".*bamboo-specs\\.yml$", // Atlassian Bamboo
          ".*\\.teamcity\\.settings\\.xml$", // TeamCity
          ".*\\.drone\\.yml$", // Drone CI
          ".*codebuild\\.spec\\.yml$", // AWS CodeBuild
          ".*buildkite\\.yml$", // Buildkite

          // Infrastructure as Code (IaC) files that can also act as part of build pipelines
          ".*\\.tf$", // Terraform
          ".*cloudbuild\\.yaml$", // Google Cloud Build
          ".*skaffold\\.yaml$", // Kubernetes Skaffold
          ".*pipeline\\.yaml$", // Kubernetes/Openshift pipeline files

          // Other
          ".*version.json$", // Version file
          ".*version.properties$", // Version file
          ".*version.txt$", // Version file
          ".*version.yml$", // Version file
          ".*version.yaml$", // Version file
          ".*version.js" // Version file
          );

  public boolean isDocumentationFile(String filePath) {
    return DOCUMENTATION_FILES.stream().anyMatch(filePath::matches);
  }

  public boolean isTestFile(String filePath) {
    return TEST_FILES.stream().anyMatch(filePath::matches);
  }

  public boolean isBuildFile(String filePath) {
    return BUILD_FILES.stream().anyMatch(filePath::matches);
  }

  public boolean isDotFile(String filePath) {
    return filePath.matches(DOT_FILES);
  }
}
