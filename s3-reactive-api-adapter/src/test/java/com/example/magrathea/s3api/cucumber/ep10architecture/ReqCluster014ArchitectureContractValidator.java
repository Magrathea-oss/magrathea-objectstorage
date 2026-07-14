package com.example.magrathea.s3api.cucumber.ep10architecture;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Repository-reproducible source contract validator for Story BDD requirement REQ-CLUSTER-014. */
final class ReqCluster014ArchitectureContractValidator {
    private static final String CLUSTER_APPLICATION =
            "com.example.magrathea.storageengine.cluster.application";
    private static final String OBJECT_STORE_REPOSITORY_PORTS =
            "com.example.magrathea.objectstore.reactive.repository.application";
    private static final String RATIS_PACKAGE = "org.apache.ratis";
    private static final String GRPC_PACKAGE = "io.grpc";
    private static final String PROTOBUF_PACKAGE = "com.google.protobuf";
    private static final String GENERATED_REPLICA_PACKAGE =
            "com.example.magrathea.cluster.protocol.v1";
    private static final String RATIS_MODULE = "cluster-control-ratis-infrastructure";
    private static final String GRPC_MODULE = "cluster-data-grpc-infrastructure";
    private static final String ACL_MODULE =
            "object-store-reactive-repository-storage-engine-infrastructure";
    private static final String APPLICATION_MODULE = "storage-engine-cluster-application";
    private static final String PROTOCOL_MODULE = "cluster-protocol";
    private static final String DOMAIN_MODULE = "storage-engine-domain";
    private static final String S3_MODULE = "s3-reactive-api-adapter";
    private static final String BOOTSTRAP_MODULE = "bootstrap-application";
    private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(120);

    private final Path root;

    ReqCluster014ArchitectureContractValidator(Path repositoryRoot) {
        root = repositoryRoot.toAbsolutePath().normalize();
        require(Files.isDirectory(root), "repository root is not a directory: " + root);
    }

    Path root() {
        return root;
    }

    void validateRegularInput(String relativePath) {
        readRequired(resolve(relativePath));
    }

    void validateSourceRoot(String relativePath) {
        Path directory = resolve(relativePath);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw violation(directory, "missing source root");
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw violation(directory, "source root is not a real directory");
        }
        if (!Files.isReadable(directory)) {
            throw violation(directory, "source root is unreadable");
        }
    }

    ReactorSnapshot inspectReactor(String rootPomPath) {
        Path rootPom = resolve(rootPomPath);
        ProjectModel aggregator = parsePom(rootPom, ".", null);
        Element modulesElement = directChild(aggregator.document().getDocumentElement(), "modules")
                .orElseThrow(() -> violation(rootPom, "root reactor has no modules declaration"));
        List<String> moduleNames = new ArrayList<>();
        Set<String> uniqueModules = new LinkedHashSet<>();
        for (Element module : directChildren(modulesElement, "module")) {
            String declared = requiredText(module, rootPom, "module");
            Path relative;
            try {
                relative = Path.of(declared).normalize();
            } catch (RuntimeException failure) {
                throw violation(rootPom, "malformed module path " + declared, failure);
            }
            if (relative.isAbsolute() || relative.getNameCount() == 0
                    || relative.startsWith("..") || relative.toString().equals(".")) {
                throw violation(rootPom, "module path escapes the repository: " + declared);
            }
            String normalized = unix(relative);
            if (!normalized.equals(declared.replace('\\', '/'))) {
                throw violation(rootPom, "module path is not normalized: " + declared);
            }
            if (!uniqueModules.add(normalized)) {
                throw violation(rootPom, "duplicate reactor module: " + normalized);
            }
            moduleNames.add(normalized);
        }
        if (moduleNames.isEmpty()) {
            throw violation(rootPom, "root reactor module declaration is empty");
        }

        Map<String, ProjectModel> projects = new LinkedHashMap<>();
        projects.put(".", aggregator);
        for (String module : moduleNames) {
            Path modulePom = resolve(module + "/pom.xml");
            ProjectModel model = parsePom(modulePom, module, module);
            if (!model.artifactId().equals(Path.of(module).getFileName().toString())) {
                throw violation(modulePom, "artifactId " + model.artifactId()
                        + " does not identify declared module " + module);
            }
            projects.put(module, model);
        }

        List<String> trackedJavaPaths = trackedProductionJavaPaths();
        Map<String, List<String>> pathsByModule = new LinkedHashMap<>();
        moduleNames.forEach(module -> pathsByModule.put(module, new ArrayList<>()));
        for (String path : trackedJavaPaths) {
            String owner = moduleNames.stream()
                    .filter(module -> path.startsWith(module + "/src/main/java/"))
                    .max(Comparator.comparingInt(String::length))
                    .orElseThrow(() -> new AssertionError(
                            "tracked production Java is outside the declared reactor: " + path));
            pathsByModule.get(owner).add(path);
        }

        List<Path> javaInputs = trackedJavaPaths.stream().map(this::resolve).toList();
        Map<String, JavaSourceFacts> javaSources = parseJava(javaInputs);
        require(javaSources.size() == trackedJavaPaths.size(),
                "not every tracked production Java source produced an AST");
        for (String module : moduleNames) {
            List<String> paths = pathsByModule.get(module);
            if (!paths.isEmpty()) {
                Path sourceRoot = resolve(module + "/src/main/java");
                validateSourceRoot(display(sourceRoot));
            }
        }

        long profileDependencyDeclarations = projects.values().stream()
                .flatMap(project -> project.dependencies().stream())
                .filter(dependency -> dependency.profileId() != null)
                .count();
        require(profileDependencyDeclarations > 0,
                "reactor profile dependency declarations were not discovered");

        return new ReactorSnapshot(
                rootPomPath,
                List.copyOf(moduleNames),
                Collections.unmodifiableMap(projects),
                Collections.unmodifiableMap(javaSources),
                immutableLists(pathsByModule),
                profileDependencyDeclarations);
    }

    ProtoSnapshot inspectProtocols(String protoRootPath) {
        Path protoRoot = resolve(protoRootPath);
        validateSourceRoot(protoRootPath);
        List<Path> protoFiles;
        try (Stream<Path> paths = Files.walk(protoRoot)) {
            protoFiles = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".proto"))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw violation(protoRoot, "cannot enumerate protobuf declarations", failure);
        }
        if (protoFiles.isEmpty()) {
            throw violation(protoRoot, "protobuf declaration root is empty");
        }
        Map<String, ProtoFileFacts> facts = new LinkedHashMap<>();
        for (Path file : protoFiles) {
            if (Files.isSymbolicLink(file)) {
                throw violation(file, "protobuf declaration must not be a symbolic link");
            }
            ProtoFileFacts parsed = parseProto(file);
            facts.put(display(file), parsed);
        }
        return new ProtoSnapshot(protoRootPath, Collections.unmodifiableMap(facts));
    }

    LayeringResult executeLayeringGuard(String scriptPath, Path evidenceDirectory) {
        Path script = resolve(scriptPath);
        readRequired(script);
        if (!Files.isExecutable(script)) {
            throw violation(script, "layering guard is not executable");
        }
        Path output = evidenceDirectory.resolve("layering-guard.log");
        LayeringResult result = executeScript(script, output);
        require(result.exitCode() == 0,
                scriptPath + " failed with exit " + result.exitCode() + ": " + result.output());
        require(result.output().lines().anyMatch("Module layering guard passed."::equals),
                scriptPath + " did not emit its semantic success evidence");
        return result;
    }

    void assertCriticalEvidenceTracked(
            ReactorSnapshot reactor, Map<String, BoundaryEvidence> expectedEvidence) {
        require(expectedEvidence.size() == 7, "REQ-CLUSTER-014 boundary table must contain seven rows");
        for (BoundaryEvidence evidence : expectedEvidence.values()) {
            ProjectModel project = reactor.projectForPom(evidence.pomPath());
            require(project != null, "boundary POM is not a discovered reactor module: " + evidence.pomPath());
            String sourcePath = evidence.sourcePath();
            if (sourcePath.endsWith(".proto")) {
                validateRegularInput(sourcePath);
            } else {
                validateSourceRoot(sourcePath);
                List<String> tracked = reactor.javaSources().keySet().stream()
                        .filter(path -> path.startsWith(sourcePath + "/"))
                        .toList();
                require(!tracked.isEmpty(), "boundary source root has no tracked production Java: " + sourcePath);
            }
        }
    }

    void assertApplicationBoundary(ReactorSnapshot reactor) {
        ProjectModel application = reactor.project(APPLICATION_MODULE);
        requireProductionDependency(application, "storage-engine-domain");
        for (Dependency dependency : application.productionDependencies()) {
            String artifact = dependency.artifactId();
            boolean forbidden = artifact.equals(PROTOCOL_MODULE)
                    || artifact.startsWith("cluster-")
                    || artifact.endsWith("-infrastructure")
                    || dependency.groupId().equals("org.apache.ratis")
                    || dependency.groupId().equals("io.grpc")
                    || dependency.groupId().equals("com.google.protobuf")
                    || artifact.contains("ratis") || artifact.contains("grpc") || artifact.contains("protobuf");
            require(!forbidden, application.path() + " has transport/infrastructure dependency "
                    + dependency.coordinate());
        }
        List<JavaSourceFacts> sources = reactor.sources(APPLICATION_MODULE);
        require(!sources.isEmpty(), APPLICATION_MODULE + " has no tracked production sources");
        for (JavaSourceFacts source : sources) {
            require(source.packageName().startsWith(CLUSTER_APPLICATION),
                    source.path() + " is outside the cluster application package");
            assertNoReference(source, RATIS_PACKAGE, GRPC_PACKAGE, PROTOBUF_PACKAGE,
                    GENERATED_REPLICA_PACKAGE,
                    "com.example.magrathea.cluster.control",
                    "com.example.magrathea.cluster.data");
            for (String type : source.topLevelTypes().keySet()) {
                String lowered = type.toLowerCase(Locale.ROOT);
                require(!(lowered.endsWith("adapter") || lowered.endsWith("controller")
                                || lowered.endsWith("endpoint") || lowered.endsWith("stub")
                                || lowered.endsWith("server") || lowered.endsWith("client")),
                        source.path() + " exposes infrastructure/transport type " + type);
            }
        }
        Set<String> types = sources.stream()
                .flatMap(source -> source.topLevelTypes().keySet().stream())
                .collect(Collectors.toSet());
        require(types.containsAll(Set.of(
                        "ClusterControlPlanePort", "LocalArtifactPort", "ReplicaTransferPort", "ReplicaReadPort",
                        "ClusterWriteCoordinator", "ClusterRepairCoordinator", "ReferencePublicationService")),
                APPLICATION_MODULE + " does not expose the expected transport-neutral use cases and ports");
    }

    void assertProtocolBoundary(ReactorSnapshot reactor, ProtoSnapshot protocols) {
        ProjectModel protocolProject = reactor.project(PROTOCOL_MODULE);
        for (Dependency dependency : protocolProject.productionDependencies()) {
            require(!dependency.groupId().equals("com.example"),
                    protocolProject.path() + " points back into application/domain/infrastructure via "
                            + dependency.coordinate());
            require(!dependency.groupId().startsWith("org.springframework")
                            && !dependency.artifactId().startsWith("spring-"),
                    protocolProject.path() + " exposes Spring through " + dependency.coordinate());
            require(!dependency.groupId().equals("org.apache.ratis"),
                    protocolProject.path() + " couples protocol generation to Ratis");
        }

        Set<String> allServices = new LinkedHashSet<>();
        Set<String> allRpcs = new LinkedHashSet<>();
        Set<String> allMessages = new LinkedHashSet<>();
        Set<String> allIdentifiers = new LinkedHashSet<>();
        for (ProtoFileFacts file : protocols.files().values()) {
            require(file.syntax().equals("proto3"), file.path() + " is not proto3");
            require(file.packageName().equals("magrathea.cluster.v1"),
                    file.path() + " has unexpected protobuf package " + file.packageName());
            require(file.javaPackage().equals(GENERATED_REPLICA_PACKAGE),
                    file.path() + " has unexpected Java package " + file.javaPackage());
            Path relative = Path.of(protocols.rootPath()).relativize(Path.of(file.path()));
            require(StreamSupport.pathSegments(relative).contains("v1"),
                    file.path() + " is not beneath a versioned v1 protocol path");
            allServices.addAll(file.serviceRpcs().keySet());
            file.serviceRpcs().values().forEach(allRpcs::addAll);
            allMessages.addAll(file.messages());
            allIdentifiers.addAll(file.identifiers());
        }
        require(allServices.equals(Set.of("ClusterHealthService", "ReplicaTransferService")),
                "cluster protocol services are not the bounded internal health/replica transports: " + allServices);
        require(allRpcs.equals(Set.of("Check", "Stage", "Read")),
                "cluster protocol RPCs exceed the bounded node-to-node contract: " + allRpcs);
        require(allMessages.containsAll(Set.of(
                        "HealthCheckRequest", "HealthCheckResponse", "ReplicaStageMetadata",
                        "StageReplicaRequest", "ReplicaPayloadFrame", "StageReplicaResponse",
                        "ReadReplicaRequest")),
                "cluster protocol declarations omit required node/artifact transport messages");

        Set<String> forbiddenIdentifiers = Set.of(
                "s3", "s3api", "spring", "springapi", "http", "rest", "controller",
                "bucket", "objectkey", "multipart", "tagging", "acl", "storagepolicy",
                "placement", "quorum", "antientropy", "rebalance", "orphancleanup",
                "erasurecoding", "domainpolicy", "decisionengine");
        for (String identifier : allIdentifiers) {
            String normalized = identifier.toLowerCase(Locale.ROOT).replace("_", "");
            require(!forbiddenIdentifiers.contains(normalized),
                    "cluster protocol contains S3/Spring/domain-decision identifier " + identifier);
        }
    }

    void assertRatisBoundary(ReactorSnapshot reactor) {
        ProjectModel ratis = reactor.project(RATIS_MODULE);
        requireProductionDependency(ratis, APPLICATION_MODULE);
        require(ratis.productionDependencies().stream()
                        .anyMatch(dependency -> dependency.groupId().equals("org.apache.ratis")),
                ratis.path() + " has no Apache Ratis production dependency");
        assertNoProductionDependency(ratis, PROTOCOL_MODULE, GRPC_MODULE);

        List<ReferenceHit> hits = reactor.referenceHits(RATIS_PACKAGE);
        require(!hits.isEmpty(), "no Apache Ratis source reference was inspected");
        assertHitsOnlyUnder(hits, RATIS_MODULE + "/src/main/java");
        for (ProjectModel project : reactor.moduleProjects()) {
            boolean usesRatis = project.productionDependencies().stream()
                    .anyMatch(dependency -> dependency.groupId().equals("org.apache.ratis"));
            require(!usesRatis || project.module().equals(RATIS_MODULE),
                    project.path() + " has an Apache Ratis dependency outside Ratis infrastructure");
        }
        for (JavaSourceFacts source : reactor.sources(RATIS_MODULE)) {
            assertNoReference(source, GENERATED_REPLICA_PACKAGE, GRPC_PACKAGE, PROTOBUF_PACKAGE);
        }
    }

    void assertGrpcBoundary(ReactorSnapshot reactor) {
        ProjectModel grpc = reactor.project(GRPC_MODULE);
        requireProductionDependency(grpc, APPLICATION_MODULE);
        requireProductionDependency(grpc, PROTOCOL_MODULE);
        require(grpc.productionDependencies().stream()
                        .anyMatch(dependency -> dependency.groupId().equals("io.grpc")),
                grpc.path() + " has no grpc-java production dependency");
        assertNoProductionDependency(grpc, RATIS_MODULE);

        List<ReferenceHit> grpcHits = reactor.referenceHits(GRPC_PACKAGE);
        List<ReferenceHit> protobufHits = reactor.referenceHits(PROTOBUF_PACKAGE);
        List<ReferenceHit> generatedHits = reactor.referenceHits(GENERATED_REPLICA_PACKAGE);
        require(!grpcHits.isEmpty() && !protobufHits.isEmpty() && !generatedHits.isEmpty(),
                "gRPC/protobuf/generated-stub source evidence is incomplete");
        assertHitsOnlyUnder(grpcHits, GRPC_MODULE + "/src/main/java");
        assertHitsOnlyUnder(protobufHits, GRPC_MODULE + "/src/main/java");
        assertHitsOnlyUnder(generatedHits, GRPC_MODULE + "/src/main/java");

        for (ProjectModel project : reactor.moduleProjects()) {
            for (Dependency dependency : project.productionDependencies()) {
                if (dependency.groupId().equals("io.grpc")
                        || dependency.groupId().equals("com.google.protobuf")) {
                    require(Set.of(PROTOCOL_MODULE, GRPC_MODULE).contains(project.module()),
                            project.path() + " has direct gRPC/protobuf dependency outside protocol/data infrastructure: "
                                    + dependency.coordinate());
                }
                if (dependency.artifactId().equals(PROTOCOL_MODULE)) {
                    boolean grpcConsumer = project.module().equals(GRPC_MODULE);
                    boolean reportingAggregator = project.packaging().equals("pom")
                            && reactor.sources(project.module()).isEmpty();
                    require(grpcConsumer || reportingAggregator,
                            project.path() + " consumes generated cluster protocol outside gRPC infrastructure "
                                    + "or a source-free reporting aggregator");
                }
            }
        }
    }

    void assertPureDomainBoundary(ReactorSnapshot reactor) {
        ProjectModel domain = reactor.project(DOMAIN_MODULE);
        require(domain.productionDependencies().isEmpty(),
                domain.path() + " must have zero non-test dependencies but has "
                        + domain.productionDependencies().stream().map(Dependency::coordinate).toList());
        List<String> forbidden = List.of(
                RATIS_PACKAGE, GRPC_PACKAGE, PROTOBUF_PACKAGE, "org.testcontainers",
                "org.springframework", "reactor.", "java.io", "java.nio.file",
                "java.nio.channels", "java.net", "java.net.http", "java.time.Clock",
                "java.security.cert", "java.security.KeyStore", "javax.net.ssl",
                "org.springframework.retry", "reactor.util.retry", "io.github.resilience4j.retry");
        for (JavaSourceFacts source : reactor.sources(DOMAIN_MODULE)) {
            require(source.packageName().startsWith("com.example.magrathea.storageengine.domain"),
                    source.path() + " is outside the storage-engine domain package");
            assertNoReference(source, forbidden.toArray(String[]::new));
        }
    }

    void assertAclAndBootstrapBoundary(ReactorSnapshot reactor) {
        ProjectModel acl = reactor.project(ACL_MODULE);
        requireProductionDependency(acl, "object-store-reactive-repository-application");
        requireProductionDependency(acl, APPLICATION_MODULE);
        assertNoProductionDependency(acl, PROTOCOL_MODULE, RATIS_MODULE, GRPC_MODULE);

        List<JavaSourceFacts> bridges = reactor.javaSources().values().stream()
                .filter(source -> source.references(OBJECT_STORE_REPOSITORY_PORTS))
                .filter(source -> source.references(CLUSTER_APPLICATION))
                .toList();
        require(!bridges.isEmpty(), "no real Object Store repository-to-cluster application bridge was found");
        for (JavaSourceFacts bridge : bridges) {
            require(bridge.path().startsWith(ACL_MODULE + "/src/main/java/"),
                    bridge.path() + " creates an Object Store-to-cluster bridge outside the ACL adapter");
            require(bridge.topLevelTypes().keySet().stream().anyMatch(type -> type.contains("Repository")),
                    bridge.path() + " does not implement an Object Store repository boundary");
        }

        ProjectModel bootstrap = reactor.project(BOOTSTRAP_MODULE);
        requireProductionDependency(bootstrap, RATIS_MODULE);
        requireProductionDependency(bootstrap, GRPC_MODULE);
        List<JavaSourceFacts> bootstrapSources = reactor.sources(BOOTSTRAP_MODULE);
        require(bootstrapSources.stream().anyMatch(source -> source.references(CLUSTER_APPLICATION)),
                "bootstrap does not compose cluster application ports/use cases");
        require(bootstrapSources.stream().anyMatch(source ->
                        source.references("com.example.magrathea.cluster.control.ratis")
                                && source.references("com.example.magrathea.cluster.data.grpc")),
                "bootstrap does not compose both cluster infrastructure adapters");
        for (JavaSourceFacts source : bootstrapSources) {
            assertNoReference(source, OBJECT_STORE_REPOSITORY_PORTS);
        }
    }

    void assertS3Boundary(ReactorSnapshot reactor) {
        ProjectModel s3 = reactor.project(S3_MODULE);
        for (Dependency dependency : s3.productionDependencies()) {
            require(!dependency.artifactId().equals(APPLICATION_MODULE)
                            && !dependency.artifactId().startsWith("cluster-"),
                    s3.path() + " has direct cluster production dependency " + dependency.coordinate());
        }
        for (JavaSourceFacts source : reactor.sources(S3_MODULE)) {
            List<String> clusterImports = source.imports().stream()
                    .filter(imported -> imported.contains(".cluster."))
                    .toList();
            require(clusterImports.isEmpty(),
                    source.path() + " imports cluster packages directly: " + clusterImports);
            assertNoReference(source, CLUSTER_APPLICATION, GENERATED_REPLICA_PACKAGE,
                    "com.example.magrathea.cluster.control", "com.example.magrathea.cluster.data");
        }
    }

    void assertPa6PlanningModels(ReactorSnapshot reactor, List<String> plannerNames, String sourceRoot) {
        require(plannerNames.equals(List.of(
                        "DistributedPlacementPlanner", "AntiEntropyPlanner", "RebalancePlanner")),
                "unexpected PA-6 planner set " + plannerNames);
        String packagePath = sourceRoot + "/com/example/magrathea/storageengine/domain/distributed/";
        Map<String, Map<String, String>> expectedPublicMethods = Map.of(
                "DistributedPlacementPlanner", Map.of("plan", "PlacementDecision"),
                "AntiEntropyPlanner", Map.of("plan", "HealingPlan"),
                "RebalancePlanner", Map.of(
                        "planNewNodeMoves", "RebalancePlan",
                        "evaluateFailedCopyTask", "RebalanceTaskResult"));
        for (String planner : plannerNames) {
            JavaSourceFacts source = reactor.source(packagePath + planner + ".java");
            TypeFacts type = source.type(planner);
            require(type.kind() == Tree.Kind.CLASS, source.path() + " is not a planner class");
            require(type.fieldNames().isEmpty(), source.path() + " has mutable planner instance/static fields "
                    + type.fieldNames());
            Map<String, String> publicMethods = type.methods().stream()
                    .filter(MethodFacts::isPublic)
                    .collect(Collectors.toMap(MethodFacts::name, MethodFacts::returnType));
            require(publicMethods.equals(expectedPublicMethods.get(planner)),
                    source.path() + " exposes execution-shaped methods instead of the planning contract: "
                            + publicMethods);
            assertNoReference(source, "java.io", "java.nio.file", "java.nio.channels", "java.net",
                    "java.time.Clock", "java.security.cert", "javax.net.ssl", "reactor.",
                    "org.springframework", RATIS_PACKAGE, GRPC_PACKAGE, PROTOBUF_PACKAGE);
        }

        JavaSourceFacts placement = reactor.source(packagePath + "PlacementDecision.java");
        JavaSourceFacts finding = reactor.source(packagePath + "AntiEntropyFinding.java");
        JavaSourceFacts healingPlan = reactor.source(packagePath + "HealingPlan.java");
        JavaSourceFacts healingTask = reactor.source(packagePath + "HealingTask.java");
        JavaSourceFacts move = reactor.source(packagePath + "RebalanceMove.java");
        JavaSourceFacts rebalancePlan = reactor.source(packagePath + "RebalancePlan.java");
        JavaSourceFacts taskResult = reactor.source(packagePath + "RebalanceTaskResult.java");

        assertRecordDeclares(placement, "PlacementDecision", "decisionCode", "readyForCommit");
        assertRecordDeclares(finding, "AntiEntropyFinding", "findingType");
        assertRecordDeclares(healingPlan, "HealingPlan", "findings", "tasks", "readinessStatus");
        assertRecordDeclares(healingTask, "HealingTask", "action", "status", "readiness");
        assertRecordDeclares(move, "RebalanceMove", "sourceNodeId", "targetNodeId", "committedReplicasKept");
        assertRecordDeclares(rebalancePlan, "RebalancePlan", "decision", "moves", "observableOnly");
        assertRecordDeclares(taskResult, "RebalanceTaskResult", "status", "retryEligibility",
                "originalReplicasCommitted");

        require(healingPlan.stringLiterals().containsAll(Set.of(
                        "planned-not-executed", "simulation-unrecoverable", "not-implemented")),
                healingPlan.path() + " does not distinguish a healing plan from execution");
        require(healingTask.stringLiterals().contains("planned-not-executed"),
                healingTask.path() + " does not mark healing tasks as unexecuted");
        require(rebalancePlan.type("RebalancePlan").declaration().contains("observableOnly"),
                rebalancePlan.path() + " lacks an explicit observable-only result component");
        require(taskResult.stringLiterals().containsAll(Set.of("FAILED", "PLANNED", "RETRYABLE")),
                taskResult.path() + " does not model failed/planned retry state explicitly");

        JavaSourceFacts antiEntropyPlanner = reactor.source(packagePath + "AntiEntropyPlanner.java");
        require(antiEntropyPlanner.newClassExpressions().stream()
                        .filter(expression -> expression.startsWith("new HealingTask"))
                        .allMatch(expression -> expression.contains("HealingTask.PLANNED")
                                && expression.contains("HealingTask.PLANNED_NOT_EXECUTED")),
                antiEntropyPlanner.path() + " creates a healing task without planned-not-executed evidence");
        JavaSourceFacts rebalancePlanner = reactor.source(packagePath + "RebalancePlanner.java");
        List<String> plans = rebalancePlanner.newClassExpressions().stream()
                .filter(expression -> expression.startsWith("new RebalancePlan"))
                .toList();
        require(!plans.isEmpty() && plans.stream().allMatch(ReqCluster014ArchitectureContractValidator::endsWithTrue),
                rebalancePlanner.path() + " creates a rebalance plan without observable-only=true");
        require(rebalancePlanner.newClassExpressions().stream()
                        .filter(expression -> expression.startsWith("new RebalanceTaskResult"))
                        .allMatch(expression -> expression.contains("RebalanceTaskResult.FAILED")
                                && expression.contains("RebalanceTaskResult.RETRYABLE")),
                rebalancePlanner.path() + " reports a modeled task as executed success");

        Set<String> forbiddenExecutionStatuses = Set.of(
                "copied", "repaired", "rebalanced", "reclaimed", "succeeded", "completed", "success");
        for (JavaSourceFacts source : List.of(
                placement, finding, healingPlan, healingTask, move, rebalancePlan, taskResult)) {
            for (String literal : source.stringLiterals()) {
                require(!forbiddenExecutionStatuses.contains(literal.toLowerCase(Locale.ROOT)),
                        source.path() + " reports planning data as executed status " + literal);
            }
        }
    }

    Map<String, String> captureContractInputDigests(
            ReactorSnapshot reactor, ProtoSnapshot protocols, String scriptPath) {
        LinkedHashSet<String> inputs = new LinkedHashSet<>();
        inputs.add(reactor.rootPomPath());
        reactor.projects().values().stream().map(ProjectModel::path).forEach(inputs::add);
        inputs.addAll(reactor.javaSources().keySet());
        inputs.addAll(protocols.files().keySet());
        inputs.add(scriptPath);
        Map<String, String> digests = new TreeMap<>();
        for (String input : inputs) {
            digests.put(input, sha256(readRequired(resolve(input))));
        }
        return Collections.unmodifiableMap(digests);
    }

    ProbeResult runFailClosedProbes(Path probeRoot, Map<String, String> protectedInputDigests) {
        Path normalizedProbeRoot = probeRoot.toAbsolutePath().normalize();
        Path requiredRoot = root.resolve("target/ep10/req-cluster-014/architecture-contract-probes");
        require(normalizedProbeRoot.equals(requiredRoot),
                "fail-closed probes must use " + display(requiredRoot) + " but used " + display(normalizedProbeRoot));
        deleteRecursively(normalizedProbeRoot);
        createDirectories(normalizedProbeRoot);

        int mandatory = 0;
        int unreadable = 0;
        List<String> verifiedPaths = new ArrayList<>();

        for (ProbeKind kind : ProbeKind.values()) {
            Path kindRoot = normalizedProbeRoot.resolve(kind.directory);
            createDirectories(kindRoot);

            Path missing = kindRoot.resolve("missing" + kind.suffix);
            expectPathFailure(missing, () -> probe(kind, missing));
            verifiedPaths.add(display(missing));
            mandatory++;

            Path empty = kindRoot.resolve("empty" + kind.suffix);
            writeProbe(empty, "");
            expectPathFailure(empty, () -> probe(kind, empty));
            verifiedPaths.add(display(empty));
            mandatory++;

            Path malformed = kindRoot.resolve("malformed" + kind.suffix);
            writeProbe(malformed, kind.malformed);
            if (kind == ProbeKind.SCRIPT) makeExecutable(malformed);
            expectPathFailure(malformed, () -> probe(kind, malformed));
            verifiedPaths.add(display(malformed));
            mandatory++;

            Path unreadablePath = kindRoot.resolve("unreadable" + kind.suffix);
            writeProbe(unreadablePath, kind.valid);
            if (kind == ProbeKind.SCRIPT) makeExecutable(unreadablePath);
            if (supportsUnreadableProbe(unreadablePath)) {
                Set<PosixFilePermission> original = permissions(unreadablePath);
                try {
                    setPermissions(unreadablePath, Set.of());
                    if (!Files.isReadable(unreadablePath)) {
                        expectPathFailure(unreadablePath, () -> probe(kind, unreadablePath));
                        verifiedPaths.add(display(unreadablePath));
                        unreadable++;
                    }
                } finally {
                    setPermissions(unreadablePath, original);
                }
            }
        }

        require(mandatory == 12, "missing/empty/malformed probe matrix was incomplete");
        Map<String, String> after = new TreeMap<>();
        protectedInputDigests.forEach((path, ignored) -> after.put(path, sha256(readRequired(resolve(path)))));
        require(after.equals(protectedInputDigests),
                "isolated fail-closed probes modified tracked architecture-contract inputs");
        return new ProbeResult(mandatory, unreadable, List.copyOf(verifiedPaths));
    }

    void assertArchitectureOnlyEvidence(EnumSet<EvidenceKind> evidenceKinds) {
        require(evidenceKinds.equals(EnumSet.allOf(EvidenceKind.class)),
                "architecture evidence operations are incomplete: " + evidenceKinds);
        require(evidenceKinds.stream().allMatch(EvidenceKind::architectureOnly),
                "architecture gate performed or claimed data execution");
    }

    private ProjectModel parsePom(Path pom, String module, String expectedModule) {
        Document document = parseXml(pom);
        Element project = document.getDocumentElement();
        if (!project.getLocalName().equals("project")
                || !"http://maven.apache.org/POM/4.0.0".equals(project.getNamespaceURI())) {
            throw violation(pom, "malformed Maven project root or namespace");
        }
        String modelVersion = directChild(project, "modelVersion")
                .map(element -> requiredText(element, pom, "modelVersion"))
                .orElseThrow(() -> violation(pom, "missing modelVersion"));
        if (!modelVersion.equals("4.0.0")) {
            throw violation(pom, "unsupported Maven modelVersion " + modelVersion);
        }
        String artifactId = directChild(project, "artifactId")
                .map(element -> requiredText(element, pom, "artifactId"))
                .orElseThrow(() -> violation(pom, "missing artifactId"));
        String packaging = directChild(project, "packaging")
                .map(element -> requiredText(element, pom, "packaging"))
                .orElse("jar");
        List<Dependency> dependencies = new ArrayList<>();
        directChild(project, "dependencies")
                .ifPresent(container -> parseDependencies(container, pom, module, null, dependencies));
        directChild(project, "profiles").ifPresent(profiles -> {
            for (Element profile : directChildren(profiles, "profile")) {
                String profileId = directChild(profile, "id")
                        .map(element -> requiredText(element, pom, "profile id"))
                        .orElseThrow(() -> violation(pom, "profile is missing id"));
                directChild(profile, "dependencies")
                        .ifPresent(container -> parseDependencies(container, pom, module, profileId, dependencies));
            }
        });
        Set<String> identities = new HashSet<>();
        for (Dependency dependency : dependencies) {
            String identity = dependency.profileId() + "|" + dependency.groupId() + "|"
                    + dependency.artifactId() + "|" + dependency.scope();
            if (!identities.add(identity)) {
                throw violation(pom, "duplicate direct dependency " + dependency.coordinate());
            }
        }
        return new ProjectModel(
                module, display(pom), artifactId, packaging, List.copyOf(dependencies), document);
    }

    private void parseDependencies(
            Element container,
            Path pom,
            String module,
            String profileId,
            List<Dependency> target) {
        for (Element dependency : directChildren(container, "dependency")) {
            String groupId = directChild(dependency, "groupId")
                    .map(element -> requiredText(element, pom, "dependency groupId"))
                    .orElseThrow(() -> violation(pom, "direct dependency is missing groupId"));
            String artifactId = directChild(dependency, "artifactId")
                    .map(element -> requiredText(element, pom, "dependency artifactId"))
                    .orElseThrow(() -> violation(pom, "direct dependency is missing artifactId"));
            String scope = directChild(dependency, "scope")
                    .map(element -> requiredText(element, pom, "dependency scope"))
                    .orElse("compile");
            target.add(new Dependency(module, groupId, artifactId, scope, profileId));
        }
    }

    private Document parseXml(Path path) {
        String xml = readRequired(path);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ThrowingXmlErrorHandler());
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (ParserConfigurationException | SAXException | IOException failure) {
            throw violation(path, "malformed XML", failure);
        }
    }

    private List<String> trackedProductionJavaPaths() {
        ProcessResult result = execute(List.of(
                "git", "-C", root.toString(), "ls-files", "-z", "--",
                ":(glob)*/src/main/java/**/*.java"), root, Duration.ofSeconds(30));
        if (result.exitCode() != 0) {
            throw violation(root.resolve("pom.xml"),
                    "cannot enumerate tracked production Java: " + result.textOutput());
        }
        List<String> paths = splitNullTerminated(result.output());
        if (paths.isEmpty()) {
            throw violation(root.resolve("pom.xml"), "tracked production Java inventory is empty");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String path : paths) {
            Path relative;
            try {
                relative = Path.of(path).normalize();
            } catch (RuntimeException failure) {
                throw violation(root.resolve("pom.xml"), "malformed tracked Java path " + path, failure);
            }
            if (relative.isAbsolute() || relative.startsWith("..") || !unix(relative).equals(path)) {
                throw violation(root.resolve("pom.xml"), "tracked Java path escapes repository: " + path);
            }
            if (!unique.add(path)) {
                throw violation(root.resolve("pom.xml"), "duplicate tracked Java input: " + path);
            }
        }
        return unique.stream().sorted().toList();
    }

    private Map<String, JavaSourceFacts> parseJava(List<Path> inputs) {
        if (inputs.isEmpty()) return Map.of();
        for (Path input : inputs) readRequired(input);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw violation(inputs.get(0), "JDK compiler parser is unavailable");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, JavaSourceFacts> facts = new LinkedHashMap<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(inputs);
            JavacTask task = (JavacTask) compiler.getTask(
                    null, files, diagnostics, List.of("-proc:none", "--release", "21"), null, units);
            List<CompilationUnitTree> parsed = new ArrayList<>();
            task.parse().forEach(parsed::add);
            Optional<Diagnostic<? extends JavaFileObject>> firstError = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .findFirst();
            if (firstError.isPresent()) {
                Diagnostic<? extends JavaFileObject> diagnostic = firstError.get();
                Path path = diagnostic.getSource() == null
                        ? inputs.get(0)
                        : pathFromUri(diagnostic.getSource().toUri(), inputs.get(0));
                throw violation(path, "malformed Java at line " + diagnostic.getLineNumber()
                        + ": " + diagnostic.getMessage(Locale.ROOT));
            }
            for (CompilationUnitTree unit : parsed) {
                Path path = pathFromUri(unit.getSourceFile().toUri(), inputs.get(0));
                JavaSourceFacts source = JavaSourceFacts.from(display(path), unit);
                if (source.packageName().isBlank()) {
                    throw violation(path, "production Java has no package declaration");
                }
                if (source.topLevelTypes().isEmpty()
                        && !path.getFileName().toString().equals("package-info.java")) {
                    throw violation(path, "production Java has no top-level declaration");
                }
                facts.put(source.path(), source);
            }
        } catch (IOException failure) {
            throw violation(inputs.get(0), "cannot close Java parser input", failure);
        } catch (ContractViolation failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw violation(inputs.get(0), "malformed Java", failure);
        }
        return facts;
    }

    private ProtoFileFacts parseProto(Path path) {
        String content = readRequired(path);
        return new ProtoParser(display(path), content).parse();
    }

    private LayeringResult executeScript(Path script, Path output) {
        createDirectories(output.getParent());
        ProcessBuilder builder = new ProcessBuilder("bash", display(script));
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(output.toFile());
        int exit;
        try {
            Process process = builder.start();
            if (!process.waitFor(SCRIPT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw violation(script, "script timed out after " + SCRIPT_TIMEOUT.toSeconds() + " seconds");
            }
            exit = process.exitValue();
        } catch (IOException failure) {
            throw violation(script, "cannot execute script", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw violation(script, "script execution was interrupted", failure);
        }
        String text = readOutput(output);
        if (exit != 0) {
            throw violation(script, "script exited " + exit + ": " + abbreviate(text));
        }
        return new LayeringResult(display(script), exit, text);
    }

    private void probe(ProbeKind kind, Path path) {
        switch (kind) {
            case POM -> parsePom(path, "probe", null);
            case JAVA -> parseJava(List.of(path));
            case PROTO -> parseProto(path);
            case SCRIPT -> {
                readRequired(path);
                executeScript(path, path.resolveSibling(path.getFileName() + ".log"));
            }
        }
    }

    private void expectPathFailure(Path path, Runnable action) {
        try {
            action.run();
            throw new AssertionError("probe unexpectedly accepted invalid input " + display(path));
        } catch (ContractViolation expected) {
            require(expected.getMessage().contains(display(path)),
                    "fail-closed probe did not report exact path " + display(path)
                            + "; message was: " + expected.getMessage());
        }
    }

    private boolean supportsUnreadableProbe(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            return store.supportsFileAttributeView(PosixFileAttributeView.class);
        } catch (IOException failure) {
            return false;
        }
    }

    private static Set<PosixFilePermission> permissions(Path path) {
        try {
            return Files.getPosixFilePermissions(path);
        } catch (IOException failure) {
            throw new AssertionError("cannot read probe permissions for " + path, failure);
        }
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException failure) {
            throw new AssertionError("cannot set probe permissions for " + path, failure);
        }
    }

    private static void makeExecutable(Path path) {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(path));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            if (!path.toFile().setExecutable(true, true)) {
                throw new AssertionError("cannot make probe executable: " + path);
            }
        } catch (IOException failure) {
            throw new AssertionError("cannot make probe executable: " + path, failure);
        }
    }

    private String readRequired(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw violation(path, "contract input escapes repository root");
        }
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw violation(normalized, "missing contract input");
        }
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw violation(normalized, "contract input is not a real regular file");
        }
        if (!Files.isReadable(normalized)) {
            throw violation(normalized, "unreadable contract input");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(normalized);
        } catch (IOException failure) {
            throw violation(normalized, "unreadable contract input", failure);
        }
        if (bytes.length == 0) {
            throw violation(normalized, "empty contract input");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw violation(normalized, "contract input is not valid UTF-8", failure);
        }
    }

    private static Optional<Element> directChild(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getLocalName().equals(localName)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getLocalName().equals(localName)) {
                children.add(element);
            }
        }
        return children;
    }

    private static String requiredText(Element element, Path path, String field) {
        String text = element.getTextContent() == null ? "" : element.getTextContent().trim();
        if (text.isEmpty()) throw new ContractViolation(path + ": empty " + field);
        return text;
    }

    private void assertNoProductionDependency(ProjectModel project, String... artifacts) {
        Set<String> forbidden = Set.of(artifacts);
        List<String> matches = project.productionDependencies().stream()
                .filter(dependency -> forbidden.contains(dependency.artifactId()))
                .map(Dependency::coordinate)
                .toList();
        require(matches.isEmpty(), project.path() + " has forbidden production dependencies " + matches);
    }

    private static void requireProductionDependency(ProjectModel project, String artifact) {
        require(project.productionDependencies().stream()
                        .anyMatch(dependency -> dependency.artifactId().equals(artifact)),
                project.path() + " is missing production dependency " + artifact);
    }

    private static void assertNoReference(JavaSourceFacts source, String... roots) {
        for (String root : roots) {
            require(!source.references(root), source.path() + " references forbidden API root " + root);
        }
    }

    private static void assertHitsOnlyUnder(List<ReferenceHit> hits, String allowedRoot) {
        List<ReferenceHit> outside = hits.stream()
                .filter(hit -> !hit.path().startsWith(allowedRoot + "/") && !hit.path().equals(allowedRoot))
                .toList();
        require(outside.isEmpty(), "references escaped " + allowedRoot + ": " + outside);
    }

    private static void assertRecordDeclares(
            JavaSourceFacts source, String recordName, String... semanticMembers) {
        TypeFacts record = source.type(recordName);
        require(record.kind() == Tree.Kind.RECORD, source.path() + " does not declare record " + recordName);
        for (String member : semanticMembers) {
            require(record.fieldNames().contains(member) || record.declaration().contains(member),
                    source.path() + " record " + recordName + " lacks semantic component " + member);
        }
    }

    private static boolean endsWithTrue(String expression) {
        String compact = expression.replaceAll("\\s+", "");
        return compact.endsWith(",true)");
    }

    private Path resolve(String relativePath) {
        Path candidate;
        try {
            candidate = root.resolve(relativePath).normalize();
        } catch (RuntimeException failure) {
            throw violation(root.resolve("pom.xml"), "malformed repository path " + relativePath, failure);
        }
        if (!candidate.startsWith(root)) {
            throw violation(root.resolve("pom.xml"), "repository path escapes root: " + relativePath);
        }
        return candidate;
    }

    String display(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(root)) return unix(root.relativize(absolute));
        return unix(absolute);
    }

    private ContractViolation violation(Path path, String message) {
        return new ContractViolation(display(path) + ": " + message);
    }

    private ContractViolation violation(Path path, String message, Throwable cause) {
        return new ContractViolation(display(path) + ": " + message, cause);
    }

    private static String unix(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String readOutput(Path output) {
        try {
            if (!Files.exists(output)) return "";
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new AssertionError("cannot read script output " + output, failure);
        }
    }

    private static String abbreviate(String value) {
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private static void writeProbe(Path path, String content) {
        createDirectories(path.getParent());
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new AssertionError("cannot create isolated probe " + path, failure);
        }
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException failure) {
            throw new AssertionError("cannot create directory " + path, failure);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path candidate : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(candidate);
            }
        } catch (IOException failure) {
            throw new AssertionError("cannot reset isolated probe root " + path, failure);
        }
    }

    private static Path pathFromUri(URI uri, Path fallback) {
        try {
            return Path.of(uri);
        } catch (RuntimeException failure) {
            return fallback;
        }
    }

    private static List<String> splitNullTerminated(byte[] bytes) {
        List<String> values = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                if (i > start) values.add(decodeUtf8(bytes, start, i - start));
                start = i + 1;
            }
        }
        if (start < bytes.length) values.add(decodeUtf8(bytes, start, bytes.length - start));
        return values;
    }

    private static String decodeUtf8(byte[] bytes, int offset, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new AssertionError("git emitted a non-UTF-8 path", failure);
        }
    }

    private static ProcessResult execute(
            List<String> command, Path directory, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new ProcessResult(124, output);
            }
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException failure) {
            throw new AssertionError("cannot execute " + command, failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while executing " + command, failure);
        }
    }

    private static Map<String, List<String>> immutableLists(Map<String, List<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    record BoundaryEvidence(String responsibility, String pomPath, String sourcePath) { }

    record ReactorSnapshot(
            String rootPomPath,
            List<String> modules,
            Map<String, ProjectModel> projects,
            Map<String, JavaSourceFacts> javaSources,
            Map<String, List<String>> javaPathsByModule,
            long profileDependencyDeclarations) {

        ProjectModel project(String module) {
            ProjectModel project = projects.get(module);
            if (project == null) throw new AssertionError("reactor module was not parsed: " + module);
            return project;
        }

        ProjectModel projectForPom(String pomPath) {
            return projects.values().stream().filter(project -> project.path().equals(pomPath)).findFirst().orElse(null);
        }

        List<ProjectModel> moduleProjects() {
            return projects.values().stream().filter(project -> !project.module().equals(".")).toList();
        }

        List<JavaSourceFacts> sources(String module) {
            String prefix = module + "/src/main/java/";
            return javaSources.values().stream().filter(source -> source.path().startsWith(prefix)).toList();
        }

        JavaSourceFacts source(String path) {
            JavaSourceFacts source = javaSources.get(path);
            if (source == null) throw new AssertionError("tracked production source was not parsed: " + path);
            return source;
        }

        List<ReferenceHit> referenceHits(String root) {
            List<ReferenceHit> hits = new ArrayList<>();
            for (JavaSourceFacts source : javaSources.values()) {
                source.references().stream()
                        .filter(reference -> JavaSourceFacts.matchesRoot(reference, root))
                        .map(reference -> new ReferenceHit(source.path(), reference))
                        .forEach(hits::add);
            }
            return hits;
        }
    }

    record ProjectModel(
            String module,
            String path,
            String artifactId,
            String packaging,
            List<Dependency> dependencies,
            Document document) {
        List<Dependency> productionDependencies() {
            return dependencies.stream().filter(dependency -> !dependency.scope().equals("test")).toList();
        }
    }

    record Dependency(String module, String groupId, String artifactId, String scope, String profileId) {
        String coordinate() {
            return groupId + ":" + artifactId + ":" + scope
                    + (profileId == null ? "" : "@profile=" + profileId);
        }
    }

    record JavaSourceFacts(
            String path,
            String packageName,
            Set<String> imports,
            Set<String> references,
            Map<String, TypeFacts> topLevelTypes,
            Set<String> stringLiterals,
            List<String> newClassExpressions,
            List<String> methodInvocations) {

        static JavaSourceFacts from(String path, CompilationUnitTree unit) {
            Set<String> imports = unit.getImports().stream()
                    .map(ImportTree::getQualifiedIdentifier)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            SourceScanner scanner = new SourceScanner();
            scanner.scan(unit, null);
            Set<String> references = new LinkedHashSet<>(imports);
            references.addAll(scanner.memberSelections);
            Map<String, TypeFacts> types = new LinkedHashMap<>();
            for (Tree declaration : unit.getTypeDecls()) {
                if (declaration instanceof ClassTree type && !type.getSimpleName().toString().isBlank()) {
                    List<String> fields = type.getMembers().stream()
                            .filter(member -> member instanceof VariableTree)
                            .map(member -> ((VariableTree) member).getName().toString())
                            .filter(name -> !name.isBlank())
                            .toList();
                    List<MethodFacts> methods = type.getMembers().stream()
                            .filter(member -> member instanceof MethodTree)
                            .map(member -> (MethodTree) member)
                            .filter(method -> method.getReturnType() != null)
                            .map(method -> new MethodFacts(
                                    method.getName().toString(), method.getReturnType().toString(),
                                    method.getModifiers().getFlags().contains(Modifier.PUBLIC)))
                            .toList();
                    types.put(type.getSimpleName().toString(), new TypeFacts(
                            type.getKind(), List.copyOf(fields), methods, type.toString()));
                }
            }
            return new JavaSourceFacts(
                    path,
                    unit.getPackageName() == null ? "" : unit.getPackageName().toString(),
                    Collections.unmodifiableSet(imports),
                    Collections.unmodifiableSet(references),
                    Collections.unmodifiableMap(types),
                    Collections.unmodifiableSet(scanner.stringLiterals),
                    List.copyOf(scanner.newClasses),
                    List.copyOf(scanner.invocations));
        }

        boolean references(String root) {
            return references.stream().anyMatch(reference -> matchesRoot(reference, root));
        }

        TypeFacts type(String name) {
            TypeFacts type = topLevelTypes.get(name);
            if (type == null) throw new AssertionError(path + " does not declare " + name);
            return type;
        }

        static boolean matchesRoot(String reference, String root) {
            String normalizedRoot = root.endsWith(".") ? root.substring(0, root.length() - 1) : root;
            return reference.equals(normalizedRoot)
                    || reference.startsWith(normalizedRoot + ".")
                    || reference.startsWith(normalizedRoot + "<")
                    || reference.startsWith(normalizedRoot + "[");
        }
    }

    record TypeFacts(Tree.Kind kind, List<String> fieldNames, List<MethodFacts> methods, String declaration) { }

    record MethodFacts(String name, String returnType, boolean isPublic) { }

    record ReferenceHit(String path, String reference) { }

    record ProtoSnapshot(String rootPath, Map<String, ProtoFileFacts> files) { }

    record ProtoFileFacts(
            String path,
            String syntax,
            String packageName,
            String javaPackage,
            Map<String, Set<String>> serviceRpcs,
            Set<String> messages,
            Set<String> enums,
            Set<String> identifiers) { }

    record LayeringResult(String scriptPath, int exitCode, String output) { }

    record ProbeResult(int mandatoryProbeCount, int unreadableProbeCount, List<String> exactPaths) { }

    enum EvidenceKind {
        MAVEN_MODEL(true),
        JAVA_AST(true),
        PROTO_DECLARATIONS(true),
        LAYERING_SCRIPT(true),
        FAIL_CLOSED_PROBES(true);

        private final boolean architectureOnly;

        EvidenceKind(boolean architectureOnly) {
            this.architectureOnly = architectureOnly;
        }

        boolean architectureOnly() {
            return architectureOnly;
        }
    }

    private enum ProbeKind {
        POM("pom", ".xml",
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion>",
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion><artifactId>probe</artifactId></project>"),
        JAVA("java", ".java",
                "package probe; public class Broken {",
                "package probe; public final class ValidProbe {}"),
        PROTO("proto", ".proto",
                "syntax = \"proto3\"; package probe.v1; message Broken { string value = ; }",
                "syntax = \"proto3\"; package probe.v1; option java_package = \"probe.v1\"; message Valid { string value = 1; }"),
        SCRIPT("script", ".sh",
                "#!/usr/bin/env bash\nif then\n",
                "#!/usr/bin/env bash\nexit 0\n");

        private final String directory;
        private final String suffix;
        private final String malformed;
        private final String valid;

        ProbeKind(String directory, String suffix, String malformed, String valid) {
            this.directory = directory;
            this.suffix = suffix;
            this.malformed = malformed;
            this.valid = valid;
        }
    }

    private static final class SourceScanner extends TreeScanner<Void, Void> {
        private final Set<String> memberSelections = new LinkedHashSet<>();
        private final Set<String> stringLiterals = new LinkedHashSet<>();
        private final List<String> newClasses = new ArrayList<>();
        private final List<String> invocations = new ArrayList<>();

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            memberSelections.add(node.toString());
            return super.visitMemberSelect(node, unused);
        }

        @Override
        public Void visitLiteral(LiteralTree node, Void unused) {
            if (node.getValue() instanceof String value) stringLiterals.add(value);
            return super.visitLiteral(node, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void unused) {
            newClasses.add(node.toString());
            return super.visitNewClass(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            invocations.add(node.getMethodSelect().toString());
            return super.visitMethodInvocation(node, unused);
        }
    }

    private static final class ProtoParser {
        private final String path;
        private final List<ProtoToken> tokens;
        private int index;
        private String syntax;
        private String packageName;
        private String javaPackage;
        private final Map<String, Set<String>> services = new LinkedHashMap<>();
        private final Set<String> messages = new LinkedHashSet<>();
        private final Set<String> enums = new LinkedHashSet<>();
        private final Set<String> identifiers;

        private ProtoParser(String path, String source) {
            this.path = path;
            tokens = tokenize(path, source);
            identifiers = tokens.stream()
                    .filter(token -> token.kind == ProtoTokenKind.IDENTIFIER)
                    .map(token -> token.text)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        ProtoFileFacts parse() {
            expectIdentifier("syntax");
            expectSymbol("=");
            syntax = expect(ProtoTokenKind.STRING).text;
            expectSymbol(";");
            while (!peek(ProtoTokenKind.EOF)) {
                if (acceptIdentifier("package")) {
                    if (packageName != null) fail("duplicate package declaration");
                    packageName = parseQualifiedName();
                    expectSymbol(";");
                } else if (acceptIdentifier("option")) {
                    parseOption();
                } else if (acceptIdentifier("import")) {
                    if (peekIdentifier("public") || peekIdentifier("weak")) next();
                    expect(ProtoTokenKind.STRING);
                    expectSymbol(";");
                } else if (acceptIdentifier("message")) {
                    parseMessage();
                } else if (acceptIdentifier("enum")) {
                    parseEnum();
                } else if (acceptIdentifier("service")) {
                    parseService();
                } else {
                    fail("unexpected top-level token " + current().text);
                }
            }
            if (packageName == null) fail("missing package declaration");
            if (javaPackage == null) fail("missing java_package option");
            if (messages.isEmpty() && enums.isEmpty() && services.isEmpty()) {
                fail("protobuf declaration file has no declarations");
            }
            return new ProtoFileFacts(
                    path, syntax, packageName, javaPackage, immutableSets(services),
                    Collections.unmodifiableSet(messages), Collections.unmodifiableSet(enums),
                    Collections.unmodifiableSet(identifiers));
        }

        private void parseOption() {
            String name = parseOptionName();
            expectSymbol("=");
            List<ProtoToken> value = consumeUntilSemicolon();
            if (value.isEmpty()) fail("option " + name + " has no value");
            if (name.equals("java_package")) {
                if (value.size() != 1 || value.get(0).kind != ProtoTokenKind.STRING) {
                    fail("java_package must be one string literal");
                }
                javaPackage = value.get(0).text;
            }
        }

        private String parseOptionName() {
            StringBuilder name = new StringBuilder();
            int parentheses = 0;
            while (!peekSymbol("=")) {
                ProtoToken token = next();
                if (token.kind == ProtoTokenKind.EOF || token.text.equals(";")) {
                    fail("malformed option name");
                }
                if (token.text.equals("(")) parentheses++;
                if (token.text.equals(")")) parentheses--;
                if (parentheses < 0) fail("malformed option name");
                name.append(token.text);
            }
            if (parentheses != 0 || name.isEmpty()) fail("malformed option name");
            return name.toString();
        }

        private void parseMessage() {
            String name = expect(ProtoTokenKind.IDENTIFIER).text;
            if (!messages.add(name)) fail("duplicate message " + name);
            expectSymbol("{");
            while (!acceptSymbol("}")) {
                if (peek(ProtoTokenKind.EOF)) fail("unterminated message " + name);
                if (acceptIdentifier("message")) parseMessage();
                else if (acceptIdentifier("enum")) parseEnum();
                else if (acceptIdentifier("oneof")) parseOneOf();
                else if (acceptIdentifier("option")) parseOption();
                else if (acceptIdentifier("reserved") || acceptIdentifier("extensions")) consumeUntilSemicolon();
                else parseField(true);
            }
        }

        private void parseOneOf() {
            expect(ProtoTokenKind.IDENTIFIER);
            expectSymbol("{");
            while (!acceptSymbol("}")) {
                if (peek(ProtoTokenKind.EOF)) fail("unterminated oneof");
                if (acceptIdentifier("option")) parseOption();
                else parseField(false);
            }
        }

        private void parseField(boolean labelsAllowed) {
            if (labelsAllowed && (peekIdentifier("optional") || peekIdentifier("required")
                    || peekIdentifier("repeated"))) next();
            if (acceptIdentifier("map")) {
                expectSymbol("<");
                parseQualifiedName();
                expectSymbol(",");
                parseQualifiedName();
                expectSymbol(">");
            } else {
                parseQualifiedName();
            }
            expect(ProtoTokenKind.IDENTIFIER);
            expectSymbol("=");
            ProtoToken number = expect(ProtoTokenKind.NUMBER);
            try {
                if (Long.parseLong(number.text) <= 0) fail("field number must be positive");
            } catch (NumberFormatException failure) {
                fail("invalid field number " + number.text);
            }
            if (acceptSymbol("[")) consumeBalanced("[", "]");
            expectSymbol(";");
        }

        private void parseEnum() {
            String name = expect(ProtoTokenKind.IDENTIFIER).text;
            if (!enums.add(name)) fail("duplicate enum " + name);
            expectSymbol("{");
            while (!acceptSymbol("}")) {
                if (peek(ProtoTokenKind.EOF)) fail("unterminated enum " + name);
                if (acceptIdentifier("option")) {
                    parseOption();
                } else if (acceptIdentifier("reserved")) {
                    consumeUntilSemicolon();
                } else {
                    expect(ProtoTokenKind.IDENTIFIER);
                    expectSymbol("=");
                    acceptSymbol("-");
                    expect(ProtoTokenKind.NUMBER);
                    if (acceptSymbol("[")) consumeBalanced("[", "]");
                    expectSymbol(";");
                }
            }
        }

        private void parseService() {
            String name = expect(ProtoTokenKind.IDENTIFIER).text;
            if (services.containsKey(name)) fail("duplicate service " + name);
            Set<String> rpcs = new LinkedHashSet<>();
            services.put(name, rpcs);
            expectSymbol("{");
            while (!acceptSymbol("}")) {
                if (peek(ProtoTokenKind.EOF)) fail("unterminated service " + name);
                if (acceptIdentifier("option")) {
                    parseOption();
                    continue;
                }
                expectIdentifier("rpc");
                String rpc = expect(ProtoTokenKind.IDENTIFIER).text;
                if (!rpcs.add(rpc)) fail("duplicate rpc " + rpc);
                expectSymbol("(");
                acceptIdentifier("stream");
                parseQualifiedName();
                expectSymbol(")");
                expectIdentifier("returns");
                expectSymbol("(");
                acceptIdentifier("stream");
                parseQualifiedName();
                expectSymbol(")");
                if (acceptSymbol("{")) {
                    while (!acceptSymbol("}")) {
                        if (peek(ProtoTokenKind.EOF)) fail("unterminated rpc options");
                        expectIdentifier("option");
                        parseOption();
                    }
                } else {
                    expectSymbol(";");
                }
            }
        }

        private String parseQualifiedName() {
            StringBuilder name = new StringBuilder();
            if (acceptSymbol(".")) name.append('.');
            name.append(expect(ProtoTokenKind.IDENTIFIER).text);
            while (acceptSymbol(".")) {
                name.append('.').append(expect(ProtoTokenKind.IDENTIFIER).text);
            }
            return name.toString();
        }

        private List<ProtoToken> consumeUntilSemicolon() {
            List<ProtoToken> value = new ArrayList<>();
            Deque<String> stack = new ArrayDeque<>();
            while (true) {
                ProtoToken token = next();
                if (token.kind == ProtoTokenKind.EOF) fail("missing semicolon");
                if (token.text.equals(";") && stack.isEmpty()) return value;
                if (Set.of("(", "[", "{").contains(token.text)) stack.push(token.text);
                if (Set.of(")", "]", "}").contains(token.text)) {
                    if (stack.isEmpty() || !matching(stack.pop(), token.text)) fail("unbalanced option/value");
                }
                value.add(token);
            }
        }

        private void consumeBalanced(String opening, String closing) {
            int depth = 1;
            while (depth > 0) {
                ProtoToken token = next();
                if (token.kind == ProtoTokenKind.EOF) fail("unterminated " + opening);
                if (token.text.equals(opening)) depth++;
                if (token.text.equals(closing)) depth--;
            }
        }

        private ProtoToken expect(ProtoTokenKind kind) {
            ProtoToken token = next();
            if (token.kind != kind) fail("expected " + kind + " but found " + token.text);
            return token;
        }

        private void expectIdentifier(String identifier) {
            ProtoToken token = next();
            if (token.kind != ProtoTokenKind.IDENTIFIER || !token.text.equals(identifier)) {
                fail("expected " + identifier + " but found " + token.text);
            }
        }

        private void expectSymbol(String symbol) {
            ProtoToken token = next();
            if (token.kind != ProtoTokenKind.SYMBOL || !token.text.equals(symbol)) {
                fail("expected " + symbol + " but found " + token.text);
            }
        }

        private boolean acceptIdentifier(String identifier) {
            if (!peekIdentifier(identifier)) return false;
            index++;
            return true;
        }

        private boolean acceptSymbol(String symbol) {
            if (!peekSymbol(symbol)) return false;
            index++;
            return true;
        }

        private boolean peekIdentifier(String identifier) {
            return current().kind == ProtoTokenKind.IDENTIFIER && current().text.equals(identifier);
        }

        private boolean peekSymbol(String symbol) {
            return current().kind == ProtoTokenKind.SYMBOL && current().text.equals(symbol);
        }

        private boolean peek(ProtoTokenKind kind) {
            return current().kind == kind;
        }

        private ProtoToken current() {
            return tokens.get(index);
        }

        private ProtoToken next() {
            return tokens.get(index++);
        }

        private void fail(String message) {
            ProtoToken token = tokens.get(Math.min(index, tokens.size() - 1));
            throw new ContractViolation(path + ": malformed protobuf near offset " + token.offset + ": " + message);
        }

        private static boolean matching(String opening, String closing) {
            return opening.equals("(") && closing.equals(")")
                    || opening.equals("[") && closing.equals("]")
                    || opening.equals("{") && closing.equals("}");
        }

        private static Map<String, Set<String>> immutableSets(Map<String, Set<String>> source) {
            Map<String, Set<String>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(key, Collections.unmodifiableSet(value)));
            return Collections.unmodifiableMap(result);
        }

        private static List<ProtoToken> tokenize(String path, String source) {
            List<ProtoToken> tokens = new ArrayList<>();
            int index = 0;
            while (index < source.length()) {
                char current = source.charAt(index);
                if (Character.isWhitespace(current)) {
                    index++;
                    continue;
                }
                if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                    index += 2;
                    while (index < source.length() && source.charAt(index) != '\n') index++;
                    continue;
                }
                if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
                    int start = index;
                    index += 2;
                    while (index + 1 < source.length()
                            && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) index++;
                    if (index + 1 >= source.length()) {
                        throw new ContractViolation(path + ": malformed protobuf near offset " + start
                                + ": unterminated block comment");
                    }
                    index += 2;
                    continue;
                }
                if (Character.isLetter(current) || current == '_') {
                    int start = index++;
                    while (index < source.length()) {
                        char next = source.charAt(index);
                        if (!Character.isLetterOrDigit(next) && next != '_') break;
                        index++;
                    }
                    tokens.add(new ProtoToken(ProtoTokenKind.IDENTIFIER,
                            source.substring(start, index), start));
                    continue;
                }
                if (Character.isDigit(current)) {
                    int start = index++;
                    while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
                    tokens.add(new ProtoToken(ProtoTokenKind.NUMBER, source.substring(start, index), start));
                    continue;
                }
                if (current == '"' || current == '\'') {
                    int start = index;
                    char quote = current;
                    index++;
                    StringBuilder value = new StringBuilder();
                    boolean closed = false;
                    while (index < source.length()) {
                        char next = source.charAt(index++);
                        if (next == quote) {
                            closed = true;
                            break;
                        }
                        if (next == '\\') {
                            if (index >= source.length()) break;
                            char escaped = source.charAt(index++);
                            value.append(switch (escaped) {
                                case 'n' -> '\n';
                                case 'r' -> '\r';
                                case 't' -> '\t';
                                default -> escaped;
                            });
                        } else {
                            value.append(next);
                        }
                    }
                    if (!closed) {
                        throw new ContractViolation(path + ": malformed protobuf near offset " + start
                                + ": unterminated string");
                    }
                    tokens.add(new ProtoToken(ProtoTokenKind.STRING, value.toString(), start));
                    continue;
                }
                if ("{}()[]=;.,<>+-".indexOf(current) >= 0) {
                    tokens.add(new ProtoToken(ProtoTokenKind.SYMBOL, Character.toString(current), index));
                    index++;
                    continue;
                }
                throw new ContractViolation(path + ": malformed protobuf near offset " + index
                        + ": unexpected character " + current);
            }
            tokens.add(new ProtoToken(ProtoTokenKind.EOF, "<eof>", source.length()));
            return tokens;
        }
    }

    private enum ProtoTokenKind { IDENTIFIER, STRING, NUMBER, SYMBOL, EOF }

    private record ProtoToken(ProtoTokenKind kind, String text, int offset) { }

    private record ProcessResult(int exitCode, byte[] output) {
        String textOutput() {
            return new String(output, StandardCharsets.UTF_8);
        }
    }

    private static final class ThrowingXmlErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }

    private static final class ContractViolation extends RuntimeException {
        private ContractViolation(String message) {
            super(message);
        }

        private ContractViolation(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class StreamSupport {
        private StreamSupport() { }

        static Set<String> pathSegments(Path path) {
            Set<String> segments = new LinkedHashSet<>();
            path.forEach(segment -> segments.add(segment.toString()));
            return segments;
        }
    }
}
