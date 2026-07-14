package com.example.magrathea.s3api.cucumber.ep10architecture;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact Cucumber glue for the REQ-CLUSTER-014 repository-rooted architecture Ability. */
public final class ReqCluster014ArchitectureContractSteps {
    private static final String VALIDATION_MODE =
            "repository-rooted production Maven, Java package/import, protobuf, and layering-script inspection "
                    + "with isolated fail-closed probes";
    private static final String REQUIREMENT = "REQ-CLUSTER-014";
    private static final String ROOT_POM = "pom.xml";
    private static final String LAYERING_GUARD = "scripts/check-module-layering.sh";
    private static final String PROTO_ROOT = "cluster-protocol/src/main/proto";
    private static final String PROBE_ROOT =
            "target/ep10/req-cluster-014/architecture-contract-probes";

    private final EnumSet<ReqCluster014ArchitectureContractValidator.EvidenceKind> evidenceKinds =
            EnumSet.noneOf(ReqCluster014ArchitectureContractValidator.EvidenceKind.class);

    private ReqCluster014ArchitectureContractValidator validator;
    private ReqCluster014ArchitectureContractValidator.ReactorSnapshot reactor;
    private ReqCluster014ArchitectureContractValidator.ProtoSnapshot protocols;
    private ReqCluster014ArchitectureContractValidator.LayeringResult layeringResult;
    private ReqCluster014ArchitectureContractValidator.ProbeResult probeResult;
    private Map<String, ReqCluster014ArchitectureContractValidator.BoundaryEvidence> boundaryEvidence;
    private Map<String, String> protectedInputDigests;
    private Path probeRoot;

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationModeIsSelectedForRequirement(String mode, String requirement) {
        require(mode.equals(VALIDATION_MODE), "unexpected validation mode: " + mode);
        require(requirement.equals(REQUIREMENT), "unexpected requirement: " + requirement);
        validator = new ReqCluster014ArchitectureContractValidator(repositoryRoot());
    }

    @Given("root reactor model {string} and executable layering guard {string} are architecture-contract inputs")
    public void rootReactorAndLayeringGuardAreInputs(String rootPom, String layeringGuard) {
        require(rootPom.equals(ROOT_POM), "unexpected root reactor model: " + rootPom);
        require(layeringGuard.equals(LAYERING_GUARD), "unexpected layering guard: " + layeringGuard);
        validator.validateRegularInput(rootPom);
        validator.validateRegularInput(layeringGuard);
        require(Files.isExecutable(validator.root().resolve(layeringGuard)),
                layeringGuard + " is not executable");
    }

    @Given("the inspected production boundary evidence is:")
    public void inspectedProductionBoundaryEvidenceIs(DataTable table) {
        List<String> expectedHeader = List.of(
                "responsibility", "module POM", "production source or protocol path");
        require(table.cells().get(0).equals(expectedHeader),
                "boundary evidence header differs: " + table.cells().get(0));

        Map<String, ReqCluster014ArchitectureContractValidator.BoundaryEvidence> actual =
                new LinkedHashMap<>();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String responsibility = required(row, "responsibility");
            var evidence = new ReqCluster014ArchitectureContractValidator.BoundaryEvidence(
                    responsibility,
                    required(row, "module POM"),
                    required(row, "production source or protocol path"));
            require(actual.put(responsibility, evidence) == null,
                    "duplicate boundary responsibility: " + responsibility);
        }

        Map<String, ReqCluster014ArchitectureContractValidator.BoundaryEvidence> expected =
                expectedBoundaryEvidence();
        require(actual.equals(expected),
                "boundary evidence table differs from REQ-CLUSTER-014\nexpected=" + expected + "\nactual=" + actual);
        boundaryEvidence = Map.copyOf(actual);
        for (var evidence : boundaryEvidence.values()) {
            validator.validateRegularInput(evidence.pomPath());
            if (evidence.sourcePath().endsWith(".proto")) {
                validator.validateRegularInput(evidence.sourcePath());
            } else {
                validator.validateSourceRoot(evidence.sourcePath());
            }
        }
    }

    @Given("isolated missing and malformed input probes use {string} without modifying tracked inputs")
    public void isolatedInputProbesUseTargetRoot(String relativeRoot) {
        require(relativeRoot.equals(PROBE_ROOT), "unexpected architecture probe root: " + relativeRoot);
        probeRoot = validator.root().resolve(relativeRoot).normalize();
        require(probeRoot.equals(validator.root().resolve(PROBE_ROOT)),
                "architecture probe root escaped its declared target path");
    }

    @When("the validation mode parses every module declared by {string}, every non-test Maven dependency including profile-specific declarations, and every tracked production Java package, import, and referenced type across those modules")
    public void parseReactorDependenciesAndTrackedJava(String rootPom) {
        require(rootPom.equals(ROOT_POM), "unexpected reactor input: " + rootPom);
        reactor = validator.inspectReactor(rootPom);
        validator.assertCriticalEvidenceTracked(reactor, boundaryEvidence);
        require(!reactor.modules().isEmpty()
                        && reactor.projects().size() == reactor.modules().size() + 1
                        && reactor.projects().containsKey(".")
                        && reactor.modules().stream().allMatch(reactor.projects()::containsKey),
                "not every module discovered from pom.xml was parsed");
        require(reactor.profileDependencyDeclarations() >= 2
                        && reactor.project("cluster-control-ratis-infrastructure").dependencies().stream()
                        .anyMatch(dependency -> dependency.profileId() != null)
                        && reactor.project("cluster-data-grpc-infrastructure").dependencies().stream()
                        .anyMatch(dependency -> dependency.profileId() != null),
                "profile dependency declarations were not parsed from both EP-10 infrastructure POMs");
        evidenceKinds.add(ReqCluster014ArchitectureContractValidator.EvidenceKind.MAVEN_MODEL);
        evidenceKinds.add(ReqCluster014ArchitectureContractValidator.EvidenceKind.JAVA_AST);
    }

    @When("it parses every protobuf declaration under {string} and executes {string} from the repository root")
    public void parseProtobufAndExecuteLayering(String protoRoot, String layeringGuard) {
        require(protoRoot.equals(PROTO_ROOT), "unexpected protobuf root: " + protoRoot);
        require(layeringGuard.equals(LAYERING_GUARD), "unexpected layering guard: " + layeringGuard);
        protocols = validator.inspectProtocols(protoRoot);
        evidenceKinds.add(ReqCluster014ArchitectureContractValidator.EvidenceKind.PROTO_DECLARATIONS);
        layeringResult = validator.executeLayeringGuard(
                layeringGuard, validator.root().resolve("target/ep10/req-cluster-014"));
        evidenceKinds.add(ReqCluster014ArchitectureContractValidator.EvidenceKind.LAYERING_SCRIPT);
        protectedInputDigests = validator.captureContractInputDigests(reactor, protocols, layeringGuard);
    }

    @Then("{string} exposes only transport-neutral cluster use cases and ports, with no non-test dependency or production source reference to Ratis, protobuf, generated replica stubs, grpc-java, or infrastructure modules")
    public void clusterApplicationIsTransportNeutral(String module) {
        require(module.equals("storage-engine-cluster-application"), "unexpected cluster application: " + module);
        validator.assertApplicationBoundary(reactor);
    }

    @Then("{string} is a proto3 contract with protobuf package {string} and Java package {string}")
    public void expectedReplicaProtoContract(String path, String protobufPackage, String javaPackage) {
        require(path.equals("cluster-protocol/src/main/proto/magrathea/cluster/v1/replica_service.proto"),
                "unexpected replica protocol path: " + path);
        var facts = protocols.files().get(path);
        require(facts != null, "expected replica protocol was not parsed: " + path);
        require(facts.syntax().equals("proto3"), path + " is not proto3");
        require(facts.packageName().equals(protobufPackage), path + " protobuf package differs");
        require(facts.javaPackage().equals(javaPackage), path + " Java package differs");
    }

    @Then("all {string} contracts are versioned internal node-to-node transport only, with no S3 or Spring API and no storage-policy, placement, quorum, anti-entropy, rebalance, or other domain decision logic")
    public void protocolContractsAreInternalAndDecisionFree(String module) {
        require(module.equals("cluster-protocol"), "unexpected protocol module: " + module);
        validator.assertProtocolBoundary(reactor, protocols);
    }

    @Then("Apache Ratis library package and type references rooted at {string} occur only under {string}")
    public void ratisReferencesStayInControlInfrastructure(String packageRoot, String sourceRoot) {
        require(packageRoot.equals("org.apache.ratis"), "unexpected Ratis package root: " + packageRoot);
        require(sourceRoot.equals("cluster-control-ratis-infrastructure/src/main/java"),
                "unexpected Ratis source root: " + sourceRoot);
        validator.assertRatisBoundary(reactor);
    }

    @Then("direct grpc-java and protobuf runtime package and type references rooted at {string} and {string}, and generated replica-stub references rooted at {string}, occur only under {string}")
    public void grpcReferencesStayInDataInfrastructure(
            String grpcRoot, String protobufRoot, String generatedRoot, String sourceRoot) {
        require(grpcRoot.equals("io.grpc"), "unexpected grpc-java root: " + grpcRoot);
        require(protobufRoot.equals("com.google.protobuf"), "unexpected protobuf runtime root: " + protobufRoot);
        require(generatedRoot.equals("com.example.magrathea.cluster.protocol.v1"),
                "unexpected generated protocol root: " + generatedRoot);
        require(sourceRoot.equals("cluster-data-grpc-infrastructure/src/main/java"),
                "unexpected gRPC source root: " + sourceRoot);
        validator.assertGrpcBoundary(reactor);
    }

    @Then("{string} has no non-test dependency or production package, import, or type reference to Ratis, grpc-java, protobuf, Testcontainers, Spring, filesystem I\\/O, certificate, retry-runtime, Clock, or network APIs")
    public void storageEngineDomainRemainsPure(String module) {
        require(module.equals("storage-engine-domain"), "unexpected pure-domain module: " + module);
        validator.assertPureDomainBoundary(reactor);
    }

    @Then("only production sources beneath {string} bridge Object Store repository ports to {string}")
    public void onlyAclAdapterBridgesObjectStoreToCluster(String sourceRoot, String applicationPackage) {
        require(sourceRoot.equals(
                        "object-store-reactive-repository-storage-engine-infrastructure/src/main/java"),
                "unexpected Object Store-to-Storage Engine ACL root: " + sourceRoot);
        require(applicationPackage.equals("com.example.magrathea.storageengine.cluster.application"),
                "unexpected cluster application package: " + applicationPackage);
        validator.assertAclAndBootstrapBoundary(reactor);
    }

    @Then("{string} has no non-test dependency on {string} or any {string} module, while {string} has no cluster package import")
    public void s3AdapterHasNoClusterCoupling(
            String pomPath, String applicationArtifact, String clusterPattern, String sourceRoot) {
        require(pomPath.equals("s3-reactive-api-adapter/pom.xml"), "unexpected S3 POM: " + pomPath);
        require(applicationArtifact.equals("storage-engine-cluster-application"),
                "unexpected forbidden application artifact: " + applicationArtifact);
        require(clusterPattern.equals("cluster-*"), "unexpected cluster artifact pattern: " + clusterPattern);
        require(sourceRoot.equals("s3-reactive-api-adapter/src/main/java"),
                "unexpected S3 production source root: " + sourceRoot);
        validator.assertS3Boundary(reactor);
    }

    @Then("PA-{int} {string}, {string}, and {string} beneath {string} remain side-effect-free planning models whose plans, findings, moves, and dry runs are never reported as copied, repaired, rebalanced, or reclaimed data")
    public void pa6PlannersRemainPlanningOnly(
            int phase,
            String placementPlanner,
            String antiEntropyPlanner,
            String rebalancePlanner,
            String sourceRoot) {
        require(phase == 6, "unexpected planner architecture phase: PA-" + phase);
        require(sourceRoot.equals("storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed"),
                "unexpected PA-6 source root: " + sourceRoot);
        validator.assertPa6PlanningModels(
                reactor,
                List.of(placementPlanner, antiEntropyPlanner, rebalancePlanner),
                "storage-engine-domain/src/main/java");
    }

    @Then("a missing, unreadable, empty, or unparseable contract input makes validation fail with its exact repository path instead of treating absent dependencies, declarations, imports, or types as compliant")
    public void invalidContractInputsFailClosedWithExactPaths() {
        require(protectedInputDigests != null && !protectedInputDigests.isEmpty(),
                "protected architecture-contract input digests were not captured");
        probeResult = validator.runFailClosedProbes(probeRoot, protectedInputDigests);
        require(probeResult.mandatoryProbeCount() == 12,
                "missing/empty/malformed probes did not cover every input parser");
        require(probeResult.unreadableProbeCount() == 0 || probeResult.unreadableProbeCount() == 4,
                "unreadable probes were only partially supported: " + probeResult.unreadableProbeCount());
        require(probeResult.exactPaths().stream().allMatch(path -> path.startsWith(PROBE_ROOT + "/")),
                "a fail-closed probe escaped the isolated target root");
        evidenceKinds.add(ReqCluster014ArchitectureContractValidator.EvidenceKind.FAIL_CLOSED_PROBES);
    }

    @Then("this architecture evidence does not claim broad or periodic anti-entropy, rebalance execution, orphan cleanup, or production readiness")
    public void architectureEvidenceMakesNoExecutionClaim() {
        validator.assertArchitectureOnlyEvidence(evidenceKinds);
        require(layeringResult != null && layeringResult.exitCode() == 0,
                "layering guard did not complete successfully");
        require(probeResult != null, "fail-closed probe evidence is absent");
        long productionDependencies = reactor.moduleProjects().stream()
                .mapToLong(project -> project.productionDependencies().size())
                .sum();
        System.out.printf(
                "REQ_CLUSTER_014_ARCHITECTURE_EVIDENCE modules=%d productionDependencies=%d "
                        + "profileDependencyDeclarations=%d javaSources=%d protoFiles=%d "
                        + "mandatoryProbes=%d unreadableProbes=%d layeringExit=%d scope=architecture-only%n",
                reactor.modules().size(),
                productionDependencies,
                reactor.profileDependencyDeclarations(),
                reactor.javaSources().size(),
                protocols.files().size(),
                probeResult.mandatoryProbeCount(),
                probeResult.unreadableProbeCount(),
                layeringResult.exitCode());
    }

    private static Map<String, ReqCluster014ArchitectureContractValidator.BoundaryEvidence>
    expectedBoundaryEvidence() {
        Map<String, ReqCluster014ArchitectureContractValidator.BoundaryEvidence> expected =
                new LinkedHashMap<>();
        add(expected,
                "transport-neutral cluster application",
                "storage-engine-cluster-application/pom.xml",
                "storage-engine-cluster-application/src/main/java");
        add(expected,
                "versioned internal cluster protocol",
                "cluster-protocol/pom.xml",
                "cluster-protocol/src/main/proto/magrathea/cluster/v1/replica_service.proto");
        add(expected,
                "Ratis control infrastructure",
                "cluster-control-ratis-infrastructure/pom.xml",
                "cluster-control-ratis-infrastructure/src/main/java");
        add(expected,
                "gRPC replica infrastructure",
                "cluster-data-grpc-infrastructure/pom.xml",
                "cluster-data-grpc-infrastructure/src/main/java");
        add(expected,
                "pure PA-6 storage-engine domain",
                "storage-engine-domain/pom.xml",
                "storage-engine-domain/src/main/java");
        add(expected,
                "Object Store to Storage Engine adapter",
                "object-store-reactive-repository-storage-engine-infrastructure/pom.xml",
                "object-store-reactive-repository-storage-engine-infrastructure/src/main/java");
        add(expected,
                "S3 API adapter",
                "s3-reactive-api-adapter/pom.xml",
                "s3-reactive-api-adapter/src/main/java");
        return Map.copyOf(expected);
    }

    private static void add(
            Map<String, ReqCluster014ArchitectureContractValidator.BoundaryEvidence> target,
            String responsibility,
            String pomPath,
            String sourcePath) {
        target.put(responsibility, new ReqCluster014ArchitectureContractValidator.BoundaryEvidence(
                responsibility, pomPath, sourcePath));
    }

    private static String required(Map<String, String> row, String column) {
        String value = row.get(column);
        require(value != null && !value.isBlank(), "boundary table has empty " + column + ": " + row);
        return value.trim();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("s3-reactive-api-adapter"))
                    && Files.isRegularFile(current.resolve("scripts/check-module-layering.sh"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("cannot locate repository root from "
                + Path.of("").toAbsolutePath().normalize());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
