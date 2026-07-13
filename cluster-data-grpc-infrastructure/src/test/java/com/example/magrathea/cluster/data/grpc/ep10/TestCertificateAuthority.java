package com.example.magrathea.cluster.data.grpc.ep10;

import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import java.io.IOException;
import java.nio.file.*;

/** Test-only OpenSSL fixture generator; production code accepts mounted PKI and never creates keys. */
final class TestCertificateAuthority {
    private final Path root;
    TestCertificateAuthority(Path root) { this.root=root; }
    Material create(String name, NodeIdentity identity) throws Exception {
        Path ca=root.resolve("ca"); Files.createDirectories(ca); Path caKey=ca.resolve("ca.key"); Path caCert=ca.resolve("ca.crt");
        if(!Files.exists(caCert)) {
            run("openssl","req","-x509","-newkey","rsa:2048","-nodes","-days","2","-subj","/CN=EP10 Test CA","-keyout",caKey.toString(),"-out",caCert.toString());
        }
        Path node=root.resolve("nodes").resolve(name);Files.createDirectories(node);Path key=node.resolve("tls.key"),csr=node.resolve("tls.csr"),cert=node.resolve("tls.crt"),ext=node.resolve("tls.ext");
        Files.writeString(ext,"basicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth,clientAuth\nsubjectAltName=URI:urn:magrathea:node:"+identity+",DNS:"+identity+"\n");
        run("openssl","req","-new","-newkey","rsa:2048","-nodes","-subj","/CN="+name,"-keyout",key.toString(),"-out",csr.toString());
        run("openssl","x509","-req","-days","2","-in",csr.toString(),"-CA",caCert.toString(),"-CAkey",caKey.toString(),"-CAcreateserial","-extfile",ext.toString(),"-out",cert.toString());
        return new Material(cert,key,caCert);
    }
    private static void run(String... command) throws Exception {
        Process p=new ProcessBuilder(command).redirectErrorStream(true).start();String output=new String(p.getInputStream().readAllBytes());
        if(p.waitFor()!=0)throw new IOException(String.join(" ",command)+" failed: "+output);
    }
    record Material(Path certificate,Path key,Path ca) { }
}
