package com.example.magrathea.s3api.cucumber.security;

import com.example.magrathea.s3api.cucumber.ObjectStoreTestApp;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = ObjectStoreTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "s3.security.enabled=true",
        "s3.security.region=us-east-1",
        "s3.security.allowed-clock-skew-seconds=900",
        "s3.security.credentials[0].access-key=AKIAMAGRATHEATEST1",
        "s3.security.credentials[0].secret-key=test-secret-key",
        "s3.security.credentials[0].principal=tenant-a-writer",
        "s3.security.credentials[1].access-key=AKIAMAGRATHEAREAD1",
        "s3.security.credentials[1].secret-key=reader-secret-key",
        "s3.security.credentials[1].principal=tenant-a-reader",
        "s3.security.allow-rules[0].principal=tenant-a-writer",
        "s3.security.allow-rules[0].action=s3:CreateBucket",
        "s3.security.allow-rules[0].bucket=secure-ingest",
        "s3.security.allow-rules[1].principal=tenant-a-writer",
        "s3.security.allow-rules[1].action=s3:PutObject",
        "s3.security.allow-rules[1].bucket=secure-ingest",
        "s3.security.allow-rules[1].key-prefix=incoming/",
        "s3.security.allow-rules[2].principal=tenant-a-writer",
        "s3.security.allow-rules[2].action=s3:GetObject",
        "s3.security.allow-rules[2].bucket=secure-ingest",
        "s3.security.allow-rules[2].key-prefix=incoming/",
        "s3.security.allow-rules[3].principal=tenant-a-writer",
        "s3.security.allow-rules[3].action=s3:CreateBucket",
        "s3.security.allow-rules[3].bucket=secure-encrypted",
        "s3.security.allow-rules[4].principal=tenant-a-writer",
        "s3.security.allow-rules[4].action=s3:PutObject",
        "s3.security.allow-rules[4].bucket=secure-encrypted",
        "s3.security.allow-rules[4].key-prefix=records/",
        "s3.security.allow-rules[5].principal=tenant-a-writer",
        "s3.security.allow-rules[5].action=s3:GetObject",
        "s3.security.allow-rules[5].bucket=secure-encrypted",
        "s3.security.allow-rules[5].key-prefix=records/",
        "s3.security.deny-rules[0].principal=tenant-a-writer",
        "s3.security.deny-rules[0].action=s3:PutObject",
        "s3.security.deny-rules[0].bucket=secure-ingest",
        "s3.security.deny-rules[0].key-prefix=incoming/blocked.csv",
        "s3.security.bucket-rules[0].bucket=secure-ingest",
        "s3.security.bucket-rules[0].owner=111122223333",
        "s3.security.bucket-rules[1].bucket=secure-public-block",
        "s3.security.bucket-rules[1].owner=111122223333",
        "s3.security.bucket-rules[1].block-public-acls=true",
        "s3.security.bucket-rules[1].public-read-keys[0]=docs/public.txt",
        "s3.security.bucket-rules[2].bucket=secure-encrypted",
        "s3.security.bucket-rules[2].owner=111122223333",
        "s3.security.bucket-rules[2].default-sse-s3=true",
        "s3.security.audit-file=target/security-audit/ep1/events.tsv",
        "s3.security.key-file=target/security-keys/ep1/master.key",
        "s3.security.encrypted-inspection-root=target/storage-engine-it/REQ-SEC-009-sse"
    }
)
@CucumberContextConfiguration
public class Ep1SecurityIdentityCucumberConfig {
}
