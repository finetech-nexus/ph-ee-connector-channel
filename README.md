
docker build -t nexusbank/ph-ee-connector-channel .
docker tag nexusbank/ph-ee-connector-channel   nexusbank/ph-ee-connector-channel:1.12.2
docker push nexusbank/ph-ee-connector-channel:1.12.2
# payment-hub-ee
Payment Hub Enterprise Edition middleware for integration to real-time payment systems.
#Auto-Trigger

# Checkstyle
Use below command to execute the checkstyle test.
```shell
./gradlew checkstyleMain
```

## Spotless
Use below command to execute the spotless apply.
```shell
./gradlew spotlessApply
```
docker buildx build \
--platform linux/amd64,linux/arm64 \
-t nexusbank/ph-ee-connector-channel:1.11.0  \
-f Dockerfile \
--push \