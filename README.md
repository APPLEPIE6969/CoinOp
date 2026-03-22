# CoinOp

A dynamic, order-book-based economy plugin for Minecraft servers.

## What This Plugin Does

This plugin creates a marketplace where players can trade items with each other. Instead of fixed prices, the market determines prices based on supply and demand - similar to how stock markets or Hypixel's Bazaar works.

**Key Features:**
- Players can place buy orders ("I want to buy 100 diamonds for $50 each")
- Players can place sell orders ("I want to sell 100 diamonds for $50 each")
- When prices match, trades happen automatically
- Instant buy/sell at current market price
- Item tooltips show current market prices
- Works across multiple servers (using Redis)
- Optional GUI for easier trading
- Anvil cost rework (no more "Too Expensive!")

## Requirements

Before installing, make sure you have:

1. **A Minecraft Server** running Paper or Spigot (versions 1.19.x - 1.21.x)
2. **Java 17+** for Minecraft 1.19-1.20, or **Java 21+** for Minecraft 1.21+
3. **An economy plugin** like EssentialsX, CMI, or PlayerPoints
4. **Vault** (will auto-download if missing)

## Server Compatibility

CoinOp works with all major Minecraft server software:

| Server | Status | Notes |
|--------|--------|-------|
| Paper | ✅ Full | Primary target - uses Paper API |
| Spigot | ✅ Full | Compatible via Spigot API |
| Bukkit | ✅ Full | Basic Bukkit API |
| Purpur | ✅ Full | Paper fork, 100% compatible |
| Pufferfish | ✅ Full | Paper fork, 100% compatible |
| Airplane | ✅ Full | Paper fork, 100% compatible |
| TacoSpigot | ✅ Full | Paper/Spigot fork, compatible |
| Akarin | ✅ Full | Paper fork, compatible |

**Minecraft Versions:**
- 1.19.x (1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4)
- 1.20.x (1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6)
- 1.21.x (1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, etc.)

## Installation

1. Download the JAR file for your Minecraft version from the releases
2. Put it in your server's `plugins` folder
3. Start the server
4. The plugin will create a `plugins/CoinOp/` folder with configuration files
5. Edit `config.yml` to customize the plugin
6. Restart the server or run `/coa reload`

## Configuration Guide

The main configuration file is `config.yml` located in `plugins/CoinOp/`. Here's a detailed explanation of each section:

### Economy Settings

```yaml
economy:
  tax-rate: 0.0
```

- `tax-rate`: A percentage taken from each trade. Set to `0.0` for no tax, `0.05` for 5% tax. The tax money disappears from the economy (it's not collected by anyone).

**Example:** If you set `0.05` and a player sells diamonds for $100, they only receive $95.

### Order Settings

```yaml
orders:
  min-amount: 1
  max-amount: 2304
  expiration-minutes: -1
```

- `min-amount`: Minimum items per order. Default is 1.
- `max-amount`: Maximum items per order. Default is 2304 (36 stacks = one double chest). Set to `-1` for unlimited.
- `expiration-minutes`: How long orders stay active. Default `-1` means orders never expire. Set to `1440` for 24 hours, `10080` for 1 week.

### Commodities Configuration

```yaml
commodities:
  allowed: []
  categories:
    ores:
      - DIAMOND
      - IRON_INGOT
      - GOLD_INGOT
    crops:
      - WHEAT
      - CARROT
```

- `allowed`: Leave empty `[]` to allow all items. Or list specific items like `[DIAMOND, IRON_INGOT]` to only allow those.
- `categories`: Groups items together for the GUI. You can add your own categories.

**Adding a new category:**
```yaml
commodities:
  categories:
    nether:
      - NETHERITE_INGOT
      - QUARTZ
      - GLOWSTONE_DUST
```

### Trade Limits

```yaml
limits:
  global-daily-limit: 0
  daily-limits:
    DIAMOND: 1000
```

- `global-daily-limit`: Maximum total items a player can trade per day. `0` means unlimited.
- `daily-limits`: Per-item limits. The example limits players to 1000 diamonds per day.

**Why use limits?** Prevents players from manipulating the market or laundering money.

### Price Bounds

```yaml
price-bounds:
  enabled: true
  global-min: 0.01
  global-max: 1000000000
  commodities:
    DIAMOND:
      min: 10.0
      max: 10000.0
```

- `enabled`: Turn price bounds on/off
- `global-min`: Minimum price for any item (default: $0.01)
- `global-max`: Maximum price for any item (default: $1 billion)
- `commodities`: Set specific limits per item

**Why use price bounds?** Prevents players from:
- Selling items for $0.01 to transfer money between accounts
- Buying items for extreme prices to manipulate the market

**Example scenario:** You want diamonds to trade between $10 and $10,000:
```yaml
price-bounds:
  enabled: true
  commodities:
    DIAMOND:
      min: 10.0
      max: 10000.0
```

Now if someone tries to place a buy order for $5, it will be rejected. If someone tries to sell for $15,000, it will be rejected.

### Multi-Server Sync (Redis)

If you run multiple servers and want them to share the same market:

```yaml
sync:
  enabled: true
  redis:
    host: localhost
    port: 6379
    password: ""
    database: 0
```

- `enabled`: Turn sync on/off
- `host`: Redis server IP address
- `port`: Redis port (default 6379)
- `password`: Redis password (leave empty if none)
- `database`: Redis database number (0-15)

**How it works:** When a player places an order on Server A, it instantly appears on Server B. Trades are synchronized across all connected servers.

**Do I need Redis?** Only if you run multiple servers. For a single server, leave `enabled: false`.

### Database Configuration

```yaml
database:
  type: sqlite
  host: localhost
  port: 3306
  name: CoinOp
  user: root
  password: ""
```

- `type`: Either `sqlite` or `mysql`
  - `sqlite`: Stores data in a local file. Good for single servers.
  - `mysql`: Connects to a MySQL server. Good for multi-server setups.
- `host`, `port`, `name`, `user`, `password`: MySQL connection details (ignored if using SQLite)

**When to use MySQL:** If you run multiple servers and want them to share order history and player data.

### Lore Display Settings

```yaml
lore:
  enabled: true
  update-interval: 20
  show-history: true
  history-points: 5
```

- `enabled`: Show market prices in item tooltips
- `update-interval`: How often to refresh prices (in ticks, 20 ticks = 1 second)
- `show-history`: Show price trend in tooltip
- `history-points`: How many price points to show

**What players see:** When they hover over a diamond in their inventory, they'll see:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CoinOp Market Data
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Buy Price: $50.00 each
Sell Price: $48.50 each
24h Volume: 125,300
Trend: ▲ Up
```

### Anvil Rework Settings

```yaml
anvil-rework:
  enabled: true
  enchantment-extraction: true
  max-cost: 0
  material-costs:
    NETHERITE_INGOT: 15
    DIAMOND: 10
```

- `enabled`: Turn the anvil rework on/off
- `enchantment-extraction`: Allow moving enchants to books
- `max-cost`: Maximum anvil cost. `0` means unlimited (no "Too Expensive!")
- `material-costs`: Base costs for different materials

**How it works:** Instead of exponential costs (2^n - 1), the anvil uses linear costs based on material rarity. This means you can combine many enchantments without hitting the "Too Expensive!" limit.

**Enchantment Extraction:** Put an enchanted item in the left slot and a book in the right slot. The enchantments will transfer to the book.

### GUI Settings

```yaml
gui:
  enabled: true
  open-on-command: true
  title: "&6CoinOp &8- &7Market"
  main-menu-size: 54
  sounds:
    open: "BLOCK_CHEST_OPEN"
    click: "UI_BUTTON_CLICK"
  icons:
    ores: "DIAMOND"
    crops: "WHEAT"
```

- `enabled`: Turn the GUI on/off
- `open-on-command`: Open GUI when player runs `/CoinOp` without arguments
- `title`: GUI title (supports color codes: `&6` = gold, `&8` = dark gray, `&7` = gray)
- `main-menu-size`: GUI size (must be multiple of 9, max 54)
- `sounds`: Sound effects for different actions
- `icons`: Which items represent each category

**Color Codes:**
- `&0` - Black
- `&1` - Dark Blue
- `&2` - Dark Green
- `&3` - Dark Aqua
- `&4` - Dark Red
- `&5` - Dark Purple
- `&6` - Gold
- `&7` - Gray
- `&8` - Dark Gray
- `&9` - Blue
- `&a` - Green
- `&b` - Aqua
- `&c` - Red
- `&d` - Light Purple
- `&e` - Yellow
- `&f` - White

## Commands

### Player Commands

| Command | Aliases | Description | Example |
|---------|---------|-------------|---------|
| `/coinop` | `/co`, `/market` | Open the CoinOp menu | `/coinop` |
| `/coinop buy <item> <amount> <price>` | `/coinbuy` | Place a buy order | `/coinbuy DIAMOND 100 50` |
| `/coinop sell <item> <amount> <price>` | `/coinsell` | Place a sell order | `/coinsell DIAMOND 100 50` |
| `/coinop instant buy <item> <amount>` | `/coininstant` | Buy at market price | `/coininstant buy DIAMOND 64` |
| `/coinop instant sell <item> <amount>` | `/coininstant` | Sell at market price | `/coininstant sell DIAMOND 64` |
| `/coinop orders` | `/coinorders` | View your active orders | `/coinorders` |
| `/coinop orders cancel <item> <id>` | - | Cancel an order | `/coinop orders cancel DIAMOND 12345` |
| `/coinop price <item>` | `/coinprice` | Check current prices | `/coinprice DIAMOND` |
| `/coinop history <item>` | - | View recent trades | `/coinop history DIAMOND` |

### Admin Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/coinadmin reload` | `/coa reload` | Reload configuration |
| `/coinadmin bounds list` | `/coa bounds list` | List all price bounds |
| `/coinadmin bounds set <item> <min> <max>` | `/coa bounds set` | Set price bounds |
| `/coinadmin bounds remove <item>` | `/coa bounds remove` | Remove price bounds |
| `/coinadmin limits list` | `/coa limits list` | List all trade limits |
| `/coinadmin limits set <item> <limit>` | `/coa limits set` | Set daily trade limit |
| `/coinadmin stats [item]` | `/coa stats` | View trading statistics |
| `/coinadmin cleanup` | `/coa cleanup` | Remove inactive orders |
| `/coinadmin sync status` | `/coa sync status` | Check Redis connection |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `CoinOp.use` | Basic CoinOp usage | Everyone |
| `CoinOp.buy` | Place buy orders | Everyone |
| `CoinOp.sell` | Place sell orders | Everyone |
| `CoinOp.instant` | Use instant buy/sell | Everyone |
| `CoinOp.orders` | View own orders | Everyone |
| `CoinOp.cancel` | Cancel own orders | Everyone |
| `CoinOp.admin` | Admin commands | OPs only |
| `CoinOp.admin.bounds` | Set price bounds | OPs only |
| `CoinOp.admin.limits` | Set trade limits | OPs only |
| `CoinOp.admin.reload` | Reload config | OPs only |
| `CoinOp.admin.sync` | Manage sync settings | OPs only |
| `CoinOp.bypass.limit` | Bypass trade limits | OPs only |
| `CoinOp.bypass.bounds` | Bypass price bounds | OPs only |

## How Trading Works

### Order Book System

The CoinOp uses an order book - the same system used by real stock markets:

1. **Buy Orders (Bids):** Players say "I want to buy X items at $Y each"
   - Highest prices are matched first
   - Example: If someone is willing to pay $55 and another $50, the $55 order trades first

2. **Sell Orders (Asks):** Players say "I want to sell X items at $Y each"
   - Lowest prices are matched first
   - Example: If someone is selling at $45 and another at $50, the $45 order trades first

3. **Matching:** When a buy order's price meets or exceeds a sell order's price, a trade happens at the sell order's price.

**Example Trade:**
- Alice places a buy order: "I want to buy 100 diamonds at $50 each"
- Bob places a sell order: "I want to sell 50 diamonds at $48 each"
- Since $50 ≥ $48, they trade: Bob sells 50 diamonds to Alice for $48 each
- Alice's order now has 50 diamonds remaining at $50 each

### Instant Buy/Sell

For players who want immediate trades:

- **Instant Buy:** Buys at the lowest available sell price
- **Instant Sell:** Sells at the highest available buy price

If no matching orders exist, the trade is rejected.

## Troubleshooting

### "Vault not found" error

The plugin will auto-download Vault on first run. If it fails:
1. Download Vault from https://www.spigotmc.org/resources/vault.34315/
2. Put it in your `plugins` folder
3. Restart the server

### "No economy plugin found" error

You need an economy plugin. Install one of these:
- EssentialsX (recommended): https://essentialsx.net/ or https://modrinth.com/plugin/essentialsx
- CMI: https://www.spigotmc.org/resources/cmi.3742/ or if you want to stay on modrinth [SunLight](https://modrinth.com/plugin/sunlightcore)
- PlayerPoints: https://www.spigotmc.org/resources/playerpoints.1997/ or https://modrinth.com/plugin/playerpoints

### "Price outside allowed bounds" error

The price is outside the limits set in config.yml. Either:
- Change the price bounds in the config
- Give the player `CoinOp.bypass.bounds` permission

### "Daily trade limit reached" error

The player has traded too much of that item today. Either:
- Wait until tomorrow (limits reset daily)
- Increase the limit in config.yml
- Give the player `CoinOp.bypass.limit` permission

### GUI not opening

Check these settings in config.yml:
```yaml
gui:
  enabled: true
  open-on-command: true
```

### Redis connection failed

1. Make sure Redis is installed and running
2. Check the connection details in config.yml
3. Test with `/coa sync status`

## Building from Source

If you want to modify the plugin:

1. Install Java 17 or Java 21
2. Clone or download the source code
3. Run `./gradlew build` (Linux/Mac) or `gradlew.bat build` (Windows)
4. Find the JARs in `versions/*/build/libs/`

## Support

- Report bugs on the [**issue tracker**](https://github.com/APPLEPIE6969/CoinOp/issues)
- For questions, ask in the [**discussion forum**](https://github.com/APPLEPIE6969/CoinOp/issues)

## License

[MIT License with Attribution](https://github.com/APPLEPIE6969/CoinOp/blob/main/LICENSE)

## Credits

**Developer:** APPLEPIE6969

Inspired by Hypixel's Bazaar system and the Minecraft community's requests for a dynamic economy plugin.
