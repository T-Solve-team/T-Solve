# Forge Hello World

This project contains a Forge app written in Javascript that displays `Hello World!` in a Jira issue panel. 

See [developer.atlassian.com/platform/forge/](https://developer.atlassian.com/platform/forge) for documentation and tutorials explaining Forge.

## Requirements

See [Set up Forge](https://developer.atlassian.com/platform/forge/set-up-forge/) for instructions to get set up.

## Point the app at your backend

The Forge remote `tsolve-backend` in `manifest.yml` has a placeholder `baseUrl` (`https://YOUR-TUNNEL.example`).
Forge manifests do not read environment variables for it, so set it yourself before `forge deploy`:

1. Start the T-Solve backend and a public HTTPS tunnel to it (for example `ngrok http 8080`), or use the staging URL.
2. In your working copy only, replace `https://YOUR-TUNNEL.example` in `manifest.yml` with that URL.
3. Run `forge deploy`, then `forge install --upgrade` if the remote changed.
4. Do not commit the URL: before `git add`, run `git diff manifest.yml` and put the placeholder back.

## Quick start

- Modify your app frontend by editing the `src/frontend/index.jsx` file.

- Modify your app backend by editing the `src/resolvers/index.js` file to define resolver functions. See [Forge resolvers](https://developer.atlassian.com/platform/forge/runtime-reference/custom-ui-resolver/) for documentation on resolver functions.

- Build and deploy your app by running:
```
forge deploy
```

- Install your app in an Atlassian site by running:
```
forge install
```

- Develop your app by running `forge tunnel` to proxy invocations locally:
```
forge tunnel
```

### Notes
- Use the `forge deploy` command when you want to persist code changes.
- Use the `forge install` command when you want to install the app on a new site.
- Once the app is installed on a site, the site picks up the new app changes you deploy without needing to rerun the install command.

## Support

See [Get help](https://developer.atlassian.com/platform/forge/get-help/) for how to get help and provide feedback.
