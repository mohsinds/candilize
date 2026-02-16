# SonarQube template: vulnerability validation

This project is configured so that CI runs SonarQube/SonarCloud and **fails the build** when the Quality Gate does not pass. That lets you enforce vulnerability and security checks in the pipeline.

## What is configured

- **Root:** `sonar-project.properties` – sources, exclusions, `sonar.qualitygate.wait=true`.
- **Root POM:** `sonar.qualitygate.wait=true` so `mvn sonar:sonar` waits for the Quality Gate and fails if it fails.
- **CI (`.github/workflows/ci.yml`):** Sonar step passes `-Dsonar.qualitygate.wait=true` and runs after `mvn verify`. If the Quality Gate fails (e.g. new vulnerabilities), the job fails.

## Enabling vulnerability validation

1. **Create/use a project** on [SonarCloud](https://sonarcloud.io) or your SonarQube server and add the repo.
2. **Add secrets** in the repo (GitHub → Settings → Secrets and variables → Actions):
   - `SONAR_TOKEN`: token from SonarCloud or SonarQube (Project or User token).
   - `SONAR_HOST_URL`: only for self-hosted SonarQube (e.g. `https://sonar.mycompany.com`).
3. **Configure the Quality Gate** on the server so that vulnerability validation is enforced:
   - **SonarCloud:** Project → Quality Gate → choose or create a gate that includes:
     - **Security Hotspots** (e.g. “Security review rating” or “Security hotspots”).
     - **Vulnerabilities** (e.g. “Vulnerabilities” = 0 or “New vulnerabilities” = 0).
   - **SonarQube:** Quality Gates → create or edit a gate → add conditions, e.g.:
     - “Vulnerabilities” = 0 (or “On New Code”).
     - “Security Hotspots” (or “Security review rating”) as needed.
4. **Assign that Quality Gate** to the `candilize` project (or the key you use in `sonar.projectKey`).

After that, every push/PR that runs CI will:

1. Build and run tests (`mvn verify`).
2. Run Sonar analysis (`mvn sonar:sonar` with `sonar.qualitygate.wait=true`).
3. **Fail the job** if the Quality Gate fails (e.g. new or open vulnerabilities, or security hotspots not reviewed).

## Running locally

```bash
export SONAR_TOKEN=your_token
# For self-hosted: export SONAR_HOST_URL=https://your-sonar.url
mvn verify sonar:sonar -Dsonar.token="$SONAR_TOKEN"
```

The build will fail locally too if the Quality Gate does not pass, so you can validate before pushing.
