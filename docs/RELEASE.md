# Create a new release

1. Update `mod_version` in `gradle.properties`.
2. Commit that change.
3. Push that commit.
4. Create a matching annotated git tag in the form `vX.Y.Z` so the tag message becomes the GitHub release notes.
5. For a short release note, run `git tag -a v1.0.1 -m "Summarise the release here"`.
6. For longer release notes, put them in a file and run `git tag -a v1.0.1 -F RELEASE_NOTES.md`.
7. Push the tag with `git push origin v1.0.1`.

The release workflow reads the annotated tag message and uses it as the GitHub release body.
If the tag has no annotation text, GitHub auto-generated release notes are used as a fallback.
GitHub Actions checks out tag pushes in a way that can obscure annotated tag contents, so the workflow fetches the remote tag object before reading the notes.

The release workflow calls MinecraftModConventions' shared `reusable-multiloader-release.yml` (v3.0.1), which builds both loader JARs and attaches the Fabric and NeoForge artifacts to the GitHub Release.
If the `MODRINTH_TOKEN` secret is configured, the same workflow creates or updates the Modrinth project and publishes both loader JARs; the `MODRINTH_PROJECT_ID` repository variable is passed to it as `modrinth_project_id`. If both the `CURSEFORGE_TOKEN` secret and the `CURSEFORGE_PROJECT_ID` repository variable are configured, it publishes both loader JARs to CurseForge.
All destinations use the same tag notes. Missing third-party credentials skip only that destination; the GitHub Release still proceeds.
