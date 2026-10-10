# Contributing to Nsec VPN

Thank you for your interest in contributing to Nsec VPN!

## Code of Conduct
We expect all contributors to adhere to standard respectful community guidelines.

## Development Workflow
1. Fork the repository and create a feature branch (`git checkout -b feature/amazing-feature`).
2. Ensure all components compile and pass tests:
   - Backend: `cd backend && npm test && npm run build`
   - Admin Panel: `cd admin-panel && npm run build`
   - Agent: `cd agent && npm run build`
   - Android App: `gradle :app:testDebugUnitTest`
3. Commit your changes following conventional commits (`feat: add node health monitor`).
4. Submit a Pull Request targeting the `main` branch.

## Security Considerations
- Never commit secrets, passwords, or keys.
- Adhere strictly to the zero-logs design: never introduce logging of user IP addresses, browsing destinations, or DNS records.
