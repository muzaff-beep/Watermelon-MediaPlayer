# libmp3lame sources go here

This directory is a placeholder. It must contain libmp3lame 3.100's source tree
(with its own CMakeLists.txt or a wrapper CMakeLists.txt producing a `mp3lame`
target) before `media-tools`'s native build will configure.

Not vendored in this commit because:
- No network access was available to fetch it when this scaffold was created.
- LGPL source distribution should be a deliberate decision (see licensing note
  in ../CMakeLists.txt), not something silently added by a generated scaffold.

## To complete this
1. Get libmp3lame 3.100 source (e.g. sourceforge.net/projects/lame/files/lame/3.100/).
2. Drop `libmp3lame/` (the folder containing lame.c, encoder.c, include/lame.h, etc.)
   directly into this directory, OR add a thin CMakeLists.txt here that points at
   wherever you vendor it, producing a target named `mp3lame`.
3. Verify it builds as a shared library (LGPL dynamic-linking requirement — see
   ../CMakeLists.txt comment).
4. Add LICENSE/LGPL attribution to the app's licenses screen (this app doesn't
   have one yet, per this scaffold's own audit — that's a separate small task).
