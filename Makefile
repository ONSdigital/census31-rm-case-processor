# Set the container runtime based on architecture, default to docker for amd64 and podman for arm64
DOCKER ?= $(shell if [ "$$(uname -m)" = "arm64" ]; then echo podman; else echo docker; fi)

install:
	CONTAINER_CLI=$(DOCKER) mvn clean install

build: install docker-build

build-no-test: install-no-test docker-build

install-no-test:
	CONTAINER_CLI=$(DOCKER) mvn clean install -Dmaven.test.skip=true -Dexec.skip=true -Djacoco.skip=true

format:
	mvn spotless:apply

format-check:
	mvn spotless:check

check:
	mvn spotless:check pmd:check

test:
	CONTAINER_CLI=$(DOCKER) mvn clean verify jacoco:report

docker-build:
	$(DOCKER) build . --platform linux/amd64 -t europe-west2-docker.pkg.dev/c31-rm-ci-prod/rm-docker-snapshot/census-rm-caseprocessor:latest

rebuild-java-healthcheck:
	$(MAKE) -C src/test/resources/java_healthcheck rebuild-java-healthcheck

megalint:  ## Run the mega-linter.
	$(DOCKER) run --platform linux/amd64 --rm \
		-v /var/run/docker.sock:/var/run/docker.sock:rw \
		-v $(shell pwd):/tmp/lint:rw \
		oxsecurity/megalinter-formatters:v8

megalint-fix:  ## Run the mega-linter and attempt to auto fix any issues.
	$(DOCKER) run --platform linux/amd64 --rm \
		-v /var/run/docker.sock:/var/run/docker.sock:rw \
		-v $(shell pwd):/tmp/lint:rw \
		-e APPLY_FIXES=all \
		oxsecurity/megalinter-formatters:v8

clean_megalint: ## Clean the temporary files.
	rm -rf megalinter-reports

lint_check: clean_megalint megalint
