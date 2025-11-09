docker build -t nexusbank/ph-ee-connector-channel .
docker tag nexusbank/ph-ee-connector-channel   nexusbank/ph-ee-connector-channel:1.1.à   
docker login
docker push nexusbank/ph-ee-connector-channel:1.1.0



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
