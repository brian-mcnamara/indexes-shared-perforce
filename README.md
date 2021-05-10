# Shared Indexes - Perforce

A Jetbrains IDE shared index plugin extension adding file hash and recent commits for Perforce
VCS projects.

## Usage:

When generating indexes, add `--additional-hash=perforce` to the `dump-shared-indexes` command
to add hashes based on perforce.

In addition to the hashes, the plugin uses cstat to determine the latest revision under all the perforce roots.
When generating the indexes, you should also use information from cstat to provide the commit.
For example:
```bash
commit=$(p4 cstat //repo/...#have | tail -3 | sed -n 's/.*change \([[:digit:]]\+\)/\1/p')
```