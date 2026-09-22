# JavaMail / android-mail needs its providers and reflection-loaded classes kept.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn com.sun.mail.**
-dontwarn javax.mail.**
-dontwarn javax.activation.**
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**

# Tink (pulled in by androidx.security:security-crypto) references annotations that are
# compile-only and absent at runtime: Error Prone's, and JSR-305's javax.annotation.
# They are annotations, so nothing reads them at run time and ignoring the dangling
# references is safe.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
