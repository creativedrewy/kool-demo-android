#!/usr/bin/env bash
# Manual Android build for the Kool demo app.
#
# Why not Gradle? The Gradle daemon talks to its client over loopback TCP,
# which is blocked in the Muse VM environment (other_tcp permission), and
# even in "Ask" mode the relay does not pass Java loopback traffic. This
# script performs the same steps with single-process tools only:
#   kotlinc (in-process) -> aapt2 -> d8 -> zipalign -> apksigner
#
# Inputs : app/src/main/java/**.kt (demo sources + MainActivity),
#          app/src/main/{res,assets,AndroidManifest.xml}
# Output : build-out/kool-demo.apk (debug-signed)
#
# Toolchain is expected under $KOOL_TOOLS (default ../tools relative to the
# workspace where this project was first set up):
#   jdk17/                 Temurin OpenJDK 17
#   android-sdk/           platform android-36 + build-tools 36.0.0
# Kool version is pinned to the published 0.19.0 artifacts on Maven Central.
set -euo pipefail
cd "$(dirname "$0")"

KOOL_VERSION=0.19.0
TOOLS=${KOOL_TOOLS:-$HOME/workspace/kool-android/tools}
export JAVA_HOME=$TOOLS/jdk17
export PATH=$JAVA_HOME/bin:$PATH
SDK=$TOOLS/android-sdk
BT=$SDK/build-tools/36.0.0
AJAR=$SDK/platforms/android-36/android.jar
WORK=build-out
DEPS=$WORK/deps
M=https://repo1.maven.org/maven2

mkdir -p $DEPS $WORK/extracted $WORK/pkg $WORK/stage $WORK/dex

dl() { # $1=maven path, $2=output name
  if [ ! -f "$DEPS/$2" ]; then echo "download $2"; curl -sL -o "$DEPS/$2" "$M/$1"; fi
}
dl de/fabmax/kool/kool-core-android/$KOOL_VERSION/kool-core-android-$KOOL_VERSION.aar kool-core.aar
dl de/fabmax/kool/kool-physics-android/$KOOL_VERSION/kool-physics-android-$KOOL_VERSION.aar kool-physics.aar
dl de/fabmax/kool/kool-physics-2d-android/$KOOL_VERSION/kool-physics-2d-android-$KOOL_VERSION.aar kool-physics-2d.aar
dl de/fabmax/physx-jni-android/2.7.1/physx-jni-android-2.7.1.aar physx.aar
dl de/fabmax/box2d-jni/box2d-jni-android/1.0.0/box2d-jni-android-1.0.0.aar box2d.aar
dl com/caverock/androidsvg-aar/1.4/androidsvg-aar-1.4.aar androidsvg.aar
dl org/jetbrains/kotlin/kotlin-stdlib/2.2.21/kotlin-stdlib-2.2.21.jar kotlin-stdlib.jar
dl org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.2/kotlinx-coroutines-core-jvm-1.10.2.jar coroutines-core.jar
dl org/jetbrains/kotlinx/kotlinx-coroutines-android/1.10.2/kotlinx-coroutines-android-1.10.2.jar coroutines-android.jar
dl org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.9.0/kotlinx-serialization-json-jvm-1.9.0.jar serialization-json.jar
dl org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.9.0/kotlinx-serialization-core-jvm-1.9.0.jar serialization-core.jar
dl org/jetbrains/kotlinx/atomicfu-jvm/0.29.0/atomicfu-jvm-0.29.0.jar atomicfu.jar
dl org/jetbrains/kotlin/kotlin-compiler-embeddable/2.2.21/kotlin-compiler-embeddable-2.2.21.jar kotlin-compiler-embeddable.jar
dl org/jetbrains/kotlin/kotlin-reflect/2.2.21/kotlin-reflect-2.2.21.jar kotlin-reflect.jar

for a in kool-core kool-physics kool-physics-2d physx box2d androidsvg; do
  mkdir -p $WORK/extracted/$a
  unzip -qo $DEPS/$a.aar -d $WORK/extracted/$a
done

# classpath resources the engine loads via getResourceAsStream (default font)
mkdir -p $WORK/fonts
if [ ! -f $WORK/fonts/fira-sans-regular.json ]; then
  git -C ${KOOL_SRC:-$HOME/workspace/kool-android/kool-src} archive v$KOOL_VERSION kool-core/src/androidMain/resources/fonts \
    | tar -x -C $WORK/fonts --strip-components=4
fi

CP="$AJAR"
for j in kool-core kool-physics kool-physics-2d physx box2d androidsvg; do CP="$CP:$WORK/extracted/$j/classes.jar"; done
for j in kotlin-stdlib coroutines-core coroutines-android serialization-json serialization-core atomicfu; do CP="$CP:$DEPS/$j.jar"; done

echo "== kotlinc =="
rm -rf $WORK/classes; mkdir -p $WORK/classes
find app/src/main/java -name "*.kt" > $WORK/sources.txt
$JAVA_HOME/bin/java -Xmx700m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=48m \
  -cp "$DEPS/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CP" -d $WORK/classes -jvm-target 17 -no-stdlib -no-reflect \
  -opt-in=kotlin.RequiresOptIn -Xcontext-parameters @$WORK/sources.txt
$JAVA_HOME/bin/jar --create --file $WORK/app-classes.jar -C $WORK/classes .

echo "== aapt2 =="
APP=app/src/main
sed -e 's|<manifest |<manifest package="de.fabmax.kool.demo" |' \
    -e 's|android:supportsRtl="true"|android:supportsRtl="true" android:extractNativeLibs="true"|' \
    $APP/AndroidManifest.xml > $WORK/pkg/AndroidManifest.xml
$BT/aapt2 compile --dir $APP/res -o $WORK/pkg/app-res.zip
$BT/aapt2 compile --dir $WORK/extracted/androidsvg/res -o $WORK/pkg/svg-res.zip
$BT/aapt2 link -o $WORK/pkg/base.apk -I $AJAR --manifest $WORK/pkg/AndroidManifest.xml \
  --min-sdk-version 28 --target-sdk-version 36 --version-code 1 --version-name 1.0 \
  --auto-add-overlay $WORK/pkg/app-res.zip $WORK/pkg/svg-res.zip

echo "== d8 =="
INPUTS="$WORK/app-classes.jar $WORK/extracted/kool-core/classes.jar $WORK/extracted/kool-physics/classes.jar $WORK/extracted/kool-physics-2d/classes.jar $WORK/extracted/physx/classes.jar $WORK/extracted/box2d/classes.jar $WORK/extracted/androidsvg/classes.jar $DEPS/kotlin-stdlib.jar $DEPS/coroutines-core.jar $DEPS/coroutines-android.jar $DEPS/serialization-json.jar $DEPS/serialization-core.jar $DEPS/atomicfu.jar"
$JAVA_HOME/bin/java -Xmx700m -cp $BT/lib/d8.jar com.android.tools.r8.D8 \
  --release --min-api 28 --lib $AJAR --output $WORK/dex $INPUTS

echo "== assemble =="
rm -rf $WORK/stage; mkdir -p $WORK/stage/lib $WORK/stage/META-INF/services
cp $WORK/dex/classes*.dex $WORK/stage/
for a in kool-core physx box2d; do cp -r $WORK/extracted/$a/jni/* $WORK/stage/lib/; done
cp -r $APP/assets $WORK/stage/assets
cp -r $WORK/fonts $WORK/stage/fonts
for j in $INPUTS; do
  $JAVA_HOME/bin/jar tf "$j" 2>/dev/null | grep "^META-INF/services/" | while read -r s; do
    unzip -p "$j" "$s" >> "$WORK/stage/$s.merged" 2>/dev/null || true
  done
done
for f in $WORK/stage/META-INF/services/*.merged; do [ -e "$f" ] && mv "$f" "${f%.merged}"; done

cp $WORK/pkg/base.apk $WORK/pkg/unsigned.apk
( cd $WORK/stage && zip -q -r ../pkg/unsigned.apk . )
$BT/zipalign -f -p 4 $WORK/pkg/unsigned.apk $WORK/pkg/aligned.apk

if [ ! -f $WORK/debug.keystore ]; then
  $JAVA_HOME/bin/keytool -genkeypair -keystore $WORK/debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
$BT/apksigner sign --ks $WORK/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out $WORK/kool-demo.apk $WORK/pkg/aligned.apk
$BT/apksigner verify $WORK/kool-demo.apk
ls -lh $WORK/kool-demo.apk
echo "BUILD OK -> $WORK/kool-demo.apk"
