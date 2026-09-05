1m 57s
Run gradle assembleDebug -Dorg.gradle.warning.mode=all

Welcome to Gradle 8.7!

Here are the highlights of this release:
 - Compiling and testing with Java 22
 - Cacheable Groovy script compilation
 - New methods in lazy collection properties

For more details see https://docs.gradle.org/8.7/release-notes.html

Starting a Gradle Daemon (subsequent builds will be faster)

> Configure project :app
Declaring client module dependencies has been deprecated. This is scheduled to be removed in Gradle 9.0. Please use component metadata rules instead. Consult the upgrading guide for further information: https://docs.gradle.org/8.7/userguide/upgrading_version_8.html#declaring_client_module_dependencies
The org.gradle.api.plugins.Convention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/8.7/userguide/upgrading_version_8.html#deprecated_access_to_conventions
Checking the license for package Android SDK Build-Tools 33.0.1 in /usr/local/lib/android/sdk/licenses
License for package Android SDK Build-Tools 33.0.1 accepted.
Preparing "Install Android SDK Build-Tools 33.0.1 v.33.0.1".
"Install Android SDK Build-Tools 33.0.1 v.33.0.1" ready.
Installing Android SDK Build-Tools 33.0.1 in /usr/local/lib/android/sdk/build-tools/33.0.1
"Install Android SDK Build-Tools 33.0.1 v.33.0.1" complete.
"Install Android SDK Build-Tools 33.0.1 v.33.0.1" finished.

> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE

> Task :app:checkDebugAarMetadata
WARNING: [Processor] Library '/home/runner/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-ui/1.2.1/8794bbc81c7dd40754e9539fcdda1ba5c999e29b/media3-ui-1.2.1.aar' contains references to both AndroidX and old support library. This seems like the library is partially migrated. Jetifier will try to rewrite the library anyway.
 Example of androidX reference: 'androidx/media3/ui/PlayerNotificationManager'
 Example of support library reference: 'android/support/v4/media/session/MediaSessionCompat$Token'

> Task :app:generateDebugResValues
> Task :app:mapDebugSourceSetPaths
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:mergeDebugResources
> Task :app:parseDebugLocalResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:compressDebugAssets
> Task :app:desugarDebugFileDependencies
> Task :app:mergeDebugJniLibFolders
> Task :app:processDebugManifestForPackage
> Task :app:checkDebugDuplicateClasses
> Task :app:processDebugResources
> Task :app:mergeExtDexDebug
> Task :app:mergeLibDexDebug
> Task :app:mergeDebugNativeLibs NO-SOURCE
> Task :app:stripDebugDebugSymbols NO-SOURCE
> Task :app:validateSigningDebug
> Task :app:writeDebugAppMetadata
> Task :app:writeDebugSigningConfigVersions
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:761:48 Expecting an expression
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:761:48 Expecting '}'
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:761:48 Expecting 'catch' or 'finally'
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:761:48 Missing '}
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:93:21 Unresolved reference: finish
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:102:21 Unresolved reference: finish

> Task :app:compileDebugKotlin FAILED
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:193:21 Unresolved reference: finish
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:219:17 Unresolved reference: finish
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:233:17 Unresolved reference: finish
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:577:28 Unresolved reference: parseString
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:636:24 Unresolved reference: parseString
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:696:28 Unresolved reference: parseString
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:708:20 Unresolved reference: has
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:712:26 Unresolved reference: isJsonArray
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:714:26 Unresolved reference: asJsonArray
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:714:38 Overload resolution ambiguity: 
public inline fun <T> Iterable<TypeVariable(T)>.forEach(action: (TypeVariable(T)) -> Unit): Unit defined in kotlin.collections
public inline fun <K, V> Map<out TypeVariable(K), TypeVariable(V)>.forEach(action: (Map.Entry<TypeVariable(K), TypeVariable(V)>) -> Unit): Unit defined in kotlin.collections
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:714:48 Cannot infer a type for this parameter. Please specify it explicitly.
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:724:29 Unresolved reference: parseChannelObject
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:731:33 Unresolved reference: isJsonObject
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:734:25 Unresolved reference: parseChannelObject
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:735:34 Unresolved reference: asJsonObject
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:747:40 Unresolved reference: has
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:752:29 Unresolved reference: isJsonArray
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:754:29 Unresolved reference: asJsonArray
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:754:41 Overload resolution ambiguity: 
public inline fun <T> Iterable<TypeVariable(T)>.forEach(action: (TypeVariable(T)) -> Unit): Unit defined in kotlin.collections
public inline fun <K, V> Map<out TypeVariable(K), TypeVariable(V)>.forEach(action: (Map.Entry<TypeVariable(K), TypeVariable(V)>) -> Unit): Unit defined in kotlin.collections
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:754:51 Cannot infer a type for this parameter. Please specify it explicitly.
e: file:///home/runner/work/StalkerIPTVPlayer/StalkerIPTVPlayer/app/src/main/java/com/stalker/iptvplayer/StalkerClient.kt:761:29 Unresolved reference: parseChannelObject

25 actionable tasks: 25 executed
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 1m 56s
Error: Process completed with exit code 1.
