# Security Policy

This project handles electrical line installers and repairers crew
dispatch coordination workflows. Treat vulnerabilities as potentially
high impact even when the demo data is synthetic — this occupation
works on live/de-energized high-voltage power lines at height,
combining electrocution risk with fall-from-height risk.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real client, crew or operator data exposure
- authorization bypass
- LineCoordGovernor bypass
- op-allowlist widening toward power-line-work execution, de-energization/re-energization/lockout-clearance authorization or utility-safety-officer override
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on client/crew data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real client/crew/operator data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
