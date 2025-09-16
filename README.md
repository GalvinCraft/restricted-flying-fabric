# Restricted Flying
[![Supported versions](https://modrinth-shields.imgalvin.me/badge/restrictedflying)](https://modrinth.com/mod/restrictedflying)

Restricted Flying is a Minecraft Mod for Fabric 1.21 that prevents flying with elytras in certain dimensions!

# Showcase
[![Watch the video](https://img.youtube.com/vi/gNEDActiWOg/maxresdefault.jpg)](https://www.youtube.com/watch?v=gNEDActiWOg)

# Commands
Commands seem to be a bit contradictory, but the list of dimensions is actually the list of dimensions where flying is allowed. By default, this is empty, meaning that no dimensions are allowed to fly in. You can add dimensions to the list using the commands below.

- `/nofly config reload` - Reloads the config file.
- `/nofly config add <dimension>` - Adds a dimension to the allowed list.
- `/nofly config remove <dimension>` - Removes a dimension from the allowed list.
- `/nofly config show` - Shows all dimensions in the allowed list.

I intend on adding more commands and features in the future, but this will suffice for now.

# Versions
| Mod Version | Loader | Game Version(s) |
|-------------|--------|-----------------|
| 1.1.1       | Fabric | 1.21.5          |
| 1.1.1       | Fabric | 1.21-1.21.4     |
| 1.1.0       | Fabric | 1.21-1.21.4     |
| 1.0.0       | Fabric | 1.21-1.21.4     |

## Changelog
### 1.1.1
- Fixed crash on 1.21.5

### 1.1.0
- Fixed a bug where the config wouldn't be loaded on startup
- New command: `/nofly config show` to show all dimensions in the allowed list
- When doing `/nofly config add <dimension>`, it will now suggest dimensions that are in the list of dimensions

# Environment
This is a server-side mod! It is not required to be downloaded client-side, unless you want to host a LAN world (or run this on your own world)
