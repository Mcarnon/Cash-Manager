{ pkgs ? import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  }
}:

let
  android = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "35" ];
    buildToolsVersions = [ "35.0.0" ];
    includeEmulator = false;
    includeNDK = false;
    includeSystemImages = false;
    useGoogleAPIs = false;
  };
  sdk = android.androidsdk;
in
pkgs.mkShell {
  buildInputs = [
    pkgs.jdk17
    pkgs.gradle
    sdk
  ];

  ANDROID_HOME = "${sdk}/libexec/android-sdk";
  ANDROID_SDK_ROOT = "${sdk}/libexec/android-sdk";
  GRADLE_OPTS = "-Dorg.gradle.daemon=false";
}
