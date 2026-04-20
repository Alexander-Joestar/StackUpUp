# runServer 鑷姩鍖栧洖褰?
## 鐩爣

鏈」鐩綋鍓嶉粯璁や互 `runServer` 鑷姩鍖栧洖褰掍綔涓轰富楠岃瘉鍏ュ彛銆?
杩欐牱鍋氱殑鍘熷洜鏄細

1. 涓嶄緷璧栨覆鏌撲笌 GUI锛岀粨鏋滄洿绋冲畾銆?2. 鑳界洿鎺ラ獙璇?`ItemStack`銆乣metadata`銆佺熆鐗╄緸鍏镐笌瀹瑰櫒鎻掓Ы涓婇檺銆?3. 鏇撮€傚悎浣滀负鍚庣画閲嶆瀯 ASM/Mixin 鐨勫洖褰掑熀绾裤€?
## 榛樿瑙勫垯鍏ュ彛

涓昏鍒欐枃浠讹細

```text
config/stackupup/main.su
```

## 鑷姩鍖栦换鍔?
鏍稿績鐭╅樀锛?
```powershell
.\gradlew.bat runServerAutoTestMatrix
```

IntelliJ 瀵煎叆 Gradle 鍚庯紝涔熷彲浠ョ洿鎺ヨ繍琛岋細

```text
2a. Run Server AutoTest Matrix
```

鍗曢」鏍蜂緥锛?
```powershell
.\gradlew.bat runServerAutoTestIngotSteel
.\gradlew.bat runServerAutoTestPlateSteel
.\gradlew.bat runServerAutoTestDustSteel
.\gradlew.bat runServerAutoTestVacuumTube
```

## 褰撳墠瑕嗙洊

`runServerAutoTestMatrix` 褰撳墠瑕嗙洊涓ょ被鐗╁搧锛?
1. 鏉愯川鍓嶇紑鐗╁搧锛?   `gregtech:meta_ingot`
   `gregtech:meta_plate`
   `gregtech:meta_dust`
2. 鏅€?metadata 鐗╁搧锛?   `gregtech:meta_item_1@516`

鐭╅樀鍚嶇О涓庣洰鏍囧搴斿叧绯伙細

```text
IngotSteel  -> gregtech:meta_ingot@324
PlateSteel  -> gregtech:meta_plate@324
DustSteel   -> gregtech:meta_dust@324
VacuumTube  -> gregtech:meta_item_1@516
```

楠岃瘉鐩爣鍖呮嫭锛?
1. 瑙勫垯瑙ｆ瀽鍚庣殑鍫嗗彔涓婇檺銆?2. 鐪熷疄 `ItemStack` 鏍堜笂闄愩€?3. `ItemStackHandler` 鎻掓Ы涓婇檺銆?4. 鎻掑叆 `128` 涓洰鏍囩墿鍝佹椂鏄惁鑳藉鏁存壒瀛樺叆鍗曟Ы銆?5. 澶栭儴鍏煎鎺㈤拡鐨勬彁鍙栦笌妲戒綅涓婇檺銆?
褰撳墠宸叉帴鍏ョ殑澶栭儴鍏煎鎺㈤拡锛?
1. `refinedstorage_grid_extract`
2. `refinedstorage_portable_grid_extract`
3. `refinedstorage_storage_monitor_extract`
4. `cyclopscore_simple_inventory_limit`
5. `colossalchests_inventory_limit`
6. `combined_inv_wrapper_limit`
7. `inv_wrapper_limit`
8. `ranged_wrapper_limit`
9. `sided_inv_wrapper_limit`
10. `slot_item_handler_limit`

鍏朵腑涓?`FixedCompatTargets` 瀵归綈鐨勫浐瀹氱洰鏍囨帰閽堢洰褰曪紝褰撳墠鐢?`FixedCompatTargets.probeTargets()` 缁熶竴澹版槑锛岄伩鍏?fixed target 璺宠繃琛ㄤ笌 dev probe 鐩綍缁х画鎵嬪啓涓や唤鍚嶅崟銆?
## 璇存槑

瀹㈡埛绔?`runClient` 浠嶇劧淇濈暀锛岀敤浜庢鏌ヤ腑閿鍒躲€佹墜鎸佹樉绀恒€丟UI 浜や簰绛夊鎴风琛屼负銆?浣嗛粯璁や富鍥炲綊搴斿綋棣栧厛鐪?`runServerAutoTestMatrix`銆?
濡傛灉闇€瑕佸湪寮€鍙戠幆澧冩寕鍏ョ涓夋柟妯＄粍寮€鍙戝寘锛屾帹鑽愭斁杩涳細

```text
local-dev-mods/
```

鏋勫缓鑴氭湰涔熷吋瀹?`run/mods/*.jar.disable`锛屼絾閭ｆ潯璺緞鏇撮€傚悎涓存椂鍏煎锛屼笉鍐嶆帹鑽愪綔涓洪暱鏈熷紑鍙戝叆鍙ｃ€?
