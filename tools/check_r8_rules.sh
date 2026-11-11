#!/usr/bin/env bash
# Confere se o R8 preservou as classes que o mecanismo de download (yt-dlp)
# usa por reflexão. Sem elas o app fecha ao preparar o mecanismo (versão 0.2.0).
set -euo pipefail

MAP="${1:-app/build/outputs/mapping/release/mapping.txt}"
if [ ! -f "$MAP" ]; then
  echo "mapping.txt não encontrado em $MAP (rode assembleRelease antes)" >&2
  exit 1
fi

fail=0
check_class() {
  if ! grep -q "^$1 -> $1:" "$MAP"; then
    echo "::error::O R8 removeu ou renomeou $1"
    fail=1
  fi
}
check_noarg_constructor() {
  local line
  line=$(grep -n "^$1 -> " "$MAP" | head -1 | cut -d: -f1)
  if [ -z "$line" ] || ! sed -n "$((line + 1)),$((line + 40))p" "$MAP" | awk '/^[^ #]/{exit} {print}' | grep -q "void <init>()"; then
    echo "::error::O R8 removeu o construtor sem argumentos de $1"
    fail=1
  fi
}

check_class com.yausername.youtubedl_android.YoutubeDL
check_class com.yausername.ffmpeg.FFmpeg
check_class org.apache.commons.compress.archivers.zip.ExtraFieldUtils
for c in AsiExtraField X5455_ExtendedTimestamp X7875_NewUnix JarMarker UnicodePathExtraField \
         UnicodeCommentExtraField Zip64ExtendedInformationExtraField X000A_NTFS; do
  check_class "org.apache.commons.compress.archivers.zip.$c"
  check_noarg_constructor "org.apache.commons.compress.archivers.zip.$c"
done

if [ "$fail" -eq 0 ]; then echo "R8: classes do mecanismo de download preservadas."; fi
exit "$fail"
