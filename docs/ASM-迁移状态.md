# ASM 杩佺Щ鐘舵€?
## 褰撳墠鍘熷垯

褰撳墠杩佺Щ閬靛惊鍥涙潯鍘熷垯锛?
1. 瑙勫垯鍐呮牳浼樺厛缁熶竴鍒?`ItemStack + metadata + OreDictionary`銆?2. 鍥哄畾鐩爣浼樺厛杩佸埌 `MixinBooter + Mixin`銆?3. 鐪熷姩鎬佺洰鏍囨殏鏃朵繚鐣欐渶灏?ASM銆?4. 鎵€鏈夎縼绉婚兘浠?`runServerAutoTestMatrix` 涓轰富鍥炲綊鍏ュ彛銆?
## 宸茶縼鍒?Mixin 鐨勫浐瀹氱洰鏍?
### 鍘熺増涓庨€氱敤璺緞

1. `Item#getItemStackLimit(ItemStack)`
2. `ItemStack#getMaxStackSize()`
3. `CommandGive`
4. `CommandReplaceItem`
5. `PacketBuffer`
6. `PacketUtil`
7. `NetHandlerPlayServer`
8. `InventoryHelper`
9. `ServerRecipeBookHelper`

### 宸茶縼鍒?late mixin 鐨勬ā缁勫浐瀹氱洰鏍?
1. `slimeknights.mantle.tileentity.TileInventory`
2. `ic2.core.block.invslot.InvSlot`
3. `appeng.tile.inventory.AppEngInternalInventory`
4. `appeng.tile.inventory.AppEngInternalAEInventory`
5. `de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase`
6. `com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler`
7. `com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandlerPortable`
8. `com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeStorageMonitor`
9. `org.cyclops.cyclopscore.inventory.SimpleInventory`

瀵瑰簲閰嶇疆鏂囦欢锛?
1. `mixins.stackupup.late.mantle.json`
2. `mixins.stackupup.late.ic2.json`
3. `mixins.stackupup.late.ae2.json`
4. `mixins.stackupup.late.actuallyadditions.json`
5. `mixins.stackupup.late.refinedstorage.json`
6. `mixins.stackupup.late.cyclopscore.json`

杩欓噷瑕佸尯鍒嗕袱涓蹇碉細

1. late mixin 妯″潡鍒楄〃锛屽彧璐熻矗鍛婅瘔 `MixinBooter` 鍝簺妯＄粍閰嶇疆鏂囦欢搴旇瑁呰浇銆?2. `FixedCompatTargets`锛屽彧璐熻矗鍛婅瘔 `DynamicCompatTransformer` 鍝簺鍥哄畾鐩爣蹇呴』璺宠繃銆?
骞朵笉鏄€滄墍鏈?late mixin 鐩爣閮借嚜鍔ㄧ瓑浜?`FixedCompatTargets`鈥濓紱鍙湁纭宸茬粡鐢辨樉寮?mixin 鐙崰璐熻矗銆佷笖闇€瑕?dynamic ASM 涓诲姩璁╄矾鐨勫浐瀹氱被锛屾墠浼氳繘鍏ラ偅寮犺烦杩囪〃銆?
杩欐剰鍛崇潃 `AE2 / Refined Storage / CyclopsCore` 鐨勫凡鐭ュ浐瀹氱洰鏍囩幇鍦ㄥ凡缁忎笉鍐嶄緷璧?`DynamicCompatTransformer` 閲岀殑鏃у墠缂€ ASM銆傚綋鍓嶄粨搴撻噷娌℃湁鍗曠嫭鐨?MoreRefinedStorage late 妯″潡閰嶇疆锛涜嫢鍚庣画鏂板鍥哄畾鐩爣锛屽簲浠ュ疄闄呮簮鐮佸拰閰嶇疆鏂囦欢涓哄噯銆?
## 褰撳墠浠嶄繚鐣欑殑 ASM 杈圭晫

### 鐪熷姩鎬佺洰鏍?
1. 鍔ㄦ€佸彂鐜扮殑 `IInventory`
2. 鍔ㄦ€佸彂鐜扮殑 `IItemHandler`
3. 鍔ㄦ€佸彂鐜扮殑 `Slot`

杩欎簺鐩爣鐨勫叡鍚岄棶棰樻槸锛?
1. 鐩爣闆嗗悎鍙湁鍦ㄨ繍琛屾椂鎵嶈兘纭畾銆?2. 寰堥毦鐢ㄩ潤鎬?class name 鍒楄〃浼橀泤瑕嗙洊銆?3. 璐哥劧鏀规垚鈥滄灇涓惧紡 mixin鈥濅細鏄捐憲澧炲姞婕忕綉涓庡吋瀹瑰洖褰掗闄┿€?4. 褰撳墠鍔ㄦ€佸眰宸插厛鍋氣€滃０鏄庢柟娉曟帰娴嬧€濓紝鍍?`PlayerInvWrapper`銆乣SlotCrafting` 杩欑鍙户鎵跨埗绫诲疄鐜般€佽嚜宸变笉鍐欐 `64` 鐨勭被浼氳鐩存帴璺宠繃銆?5. `EmptyHandler` 涓?`VanillaDoubleChestItemHandler` 杩欑被璇箟绋冲畾鐨?Forge 绫诲凡杞叆鍥哄畾璺宠繃鍚嶅崟锛屼笉鍐嶅弬涓庡姩鎬佸垽瀹氥€?6. `DynamicCompatTransformer` 鐜板凡琛ヤ笂 `null` 杈撳叆瀹夊叏锛岄伩鍏?coremod 鍦ㄦ瀬绔被鍔犺浇鍒嗘敮閲屽洜绌哄瓧鑺傜爜鐩存帴宕╂簝銆?7. 鍔ㄦ€佸眰鐨勨€滅洰鏍囩被鍨?-> 鍊欓€夋柟娉?-> 瀹為檯琛ヤ竵鏂规硶鈥濆凡鏀跺彛鍒?`DynamicCompatTargetProfile` 涓€澶勶紝鍑忓皯閲嶅瑙勫垯琛ㄣ€?8. `FixedCompatTargets` 鐜板湪鏄?dynamic ASM 鍥哄畾璺宠繃鐩爣鐨勫敮涓€浜嬪疄婧愶紱鍒嗙被娴嬭瘯銆佽鍒掓祴璇曘€乼ransformer 鍥炲綊閮界洿鎺ラ亶鍘嗚繖寮犺〃銆?9. `DynamicCompatPlan` 宸插幓鎺夋湭娑堣垂鐨?`transformedName` 鐘舵€侊紝鍙繚鐣欒ˉ涓佽浇鑽枫€?10. slash/dot 绫诲悕褰掍竴鍖栭€昏緫宸插悎骞跺埌鍗曚竴 helper锛岄伩鍏?early path 缁存姢涓や唤鐩稿悓瀹炵幇銆?11. AE2 鐨?`AppEngInternalInventory` / `AppEngInternalAEInventory` 宸茶ˉ榻?`getInventoryStackLimit` 杩斿洖鍊间慨姝ｏ紝骞惰浆鍏?fixed-target 璺宠繃琛ㄣ€?12. `DynamicCompatMethodProbe` 宸叉敼涓哄崟娆?profile-aware 鏂规硶鎵弿锛屽啀鎸夊懡涓殑 profile 鍋氬畾鍚戝眰绾у垽瀹氾紝閬垮厤閲嶅 ASM 鎵弿鍜屾棤鏁堝叧绯婚亶鍘嗐€?13. `PlayerInvWrapper`銆乣SlotCrafting` 杩欑被鍙户鎵跨埗绫昏ˉ涓佺偣鐨勬ˉ鎺ュ瓙绫伙紝褰撳墠缁х画瑙嗕负鈥滅户鎵垮櫔闊斥€濊€岄潪涓嬩竴鎵?static mixin 杩佺Щ鐩爣锛涚埗绫?mixin 宸叉壙鎷呭疄闄呰涓猴紝dynamic ASM 涔熶細鍥犳湭澹版槑鐩爣鏂规硶鑰岃烦杩囥€?
### 浠嶄繚鐣欑殑瀛楄妭鐮佽ˉ涓?
1. `ItemStackPatch`
2. 灏戦噺寮€鍙戞湡 alias / GregTech 寮€鍙戝吋瀹硅ˉ涓?
鍏朵腑 `ItemStackPatch` 浠嶇劧鏈夌嫭绔嬩环鍊硷紝鍥犱负瀹冨鐞嗙殑鏄棫鍗忚鍜屾棫搴忓垪鍖栬矾寰勯噷 `Count` 瀛楁鐨勫簳灞傛墿瀹癸紝涓嶅睘浜庨€傚悎鐩存帴鏇挎垚鏅€氫笟鍔?mixin 鐨勯偅涓€绫婚棶棰樸€?
## 宸叉竻鐞嗙殑閬楃暀 ASM 鏂囦欢

浠ヤ笅鏃ф枃浠跺凡缁忚鏃╂湡 mixin 褰诲簳鍙栦唬锛屽洜姝ゅ凡浠?`core/` 绉婚櫎锛?
1. `NetHandlerPlayServerPatch.kt`
2. `PacketBufferWriterSplice.kt`
3. `PacketUtilWriterSplice.kt`
4. `RenderEntityItemPatch.kt`
5. `RenderEntityItemSplice.kt`
6. `RenderItemPatch.kt`

杩欎竴姝ョ殑鐩殑涓嶆槸鈥滃姛鑳借縼绉烩€濓紝鑰屾槸鍑忓皯璇鍜岀淮鎶ゅ櫔闊筹細

1. 閬垮厤鎶婂巻鍙插疄鐜拌璁や负褰撳墠鐢熸晥璺緞銆?2. 璁?`core/` 鐩綍鏇村噯纭湴浠ｈ〃鈥滀粛鏈縼鍑虹殑鏈€灏?ASM 杈圭晫鈥濄€?
## 褰撳墠涓诲洖褰掑叆鍙?
```powershell
.\gradlew.bat runServerAutoTestMatrix
```

褰撳墠鑷姩鍖栫煩闃佃鐩栵細

1. `gregtech:meta_ingot@324`
2. `gregtech:meta_plate@324`
3. `gregtech:meta_dust@324`
4. `gregtech:meta_item_1@516`

楠岃瘉缁村害鍖呮嫭锛?
1. 瑙勫垯瑙ｆ瀽涓婇檺
2. 鐪熷疄 `ItemStack` 涓婇檺
3. 鎻掓Ы涓?`ItemStackHandler` 涓婇檺
4. 鎻掑叆澶т簬 64 鏁伴噺鍚庣殑瀛樺叆涓庡墿浣欒涓?5. `SimpleInventory` 鐩存帴涓婇檺
6. `portable grid` 宸﹂敭鎻愬彇涓婇檺

## 褰撳墠娴嬭瘯鎶ゆ爮

1. `runServerAutoTestMatrix`
2. `MixinBooterIntegrationTest`
3. `ItemStackPatchTest`
4. `MaxStackConstantPatchTest`
5. `DynamicCompatTargetClassifierTest`
6. `DynamicCompatPlanBuilderTest`
7. `DynamicCompatTransformerTest`
8. `ClassNameNormalizerTest`

## 鏈疆缁撴瀯鏀剁缉

1. `DevTargetSelector` 宸插苟鍥?`DevTargetRuntimeResolver`銆?2. `DevProbeEvaluator` 宸插苟鍥?`DevAutomationServerDriver`銆?3. 杩欑被浠呮湇鍔″紑鍙戣嚜鍔ㄩ獙鏀躲€佷笖鍙湁鍗曚釜娑堣垂鐐圭殑閫昏緫锛屼笉鍐嶅崟鐙媶鎴愬井鏂囦欢銆?4. `DslRuleSource` 宸叉敹鏁涗负缁熶竴瑙勫垯鍔犺浇鍏ュ彛锛宍MultiFileDslRuleSource` 宸茬Щ闄ゃ€?5. `rules` AST / 杩愯鏃跺姩浣滃凡浠庤８瀛楃涓插崗璁垏鍒板己绫诲瀷鏋氫妇锛屽悗缁户缁噸鍐欐椂鍙互鐩存帴鍦ㄧ被鍨嬪眰鏀惰竟鐣屻€?6. `DevCompatProbeRunner` 鐜板湪浼氬湪鎺㈤拡鏈褰曞埌璇锋眰閲忔垨鍙敤鎬ф鏌ュ紓甯告椂锛岃緭鍑鸿В鍖呭悗鐨勭湡瀹炲紓甯哥被鍨嬶紝閬垮厤鎶婇摼鎺ュ紓甯歌鍒や负鏅€?skip銆?7. `cyclopscore_simple_inventory_limit` 宸茬洿鎺ヨ鐩?`SimpleInventory` 鏈綋锛屼笉鍐嶅彧閫氳繃 `ColossalChests` 闂存帴楠岃瘉銆?8. `refinedstorage_portable_grid_extract` 宸插姞鍏ユ湇鍔＄鑷姩鐭╅樀锛宍ItemGridHandlerPortableMixin` 涓嶅啀鍙湁婧愮爜绾ф姢鏍忋€?
## 涓嬩竴闃舵寤鸿

1. 缁х画鎶娾€滃凡鐭ュ浐瀹氱洰鏍団€濅粠 ASM 鏀剁缉鍒?late mixin銆?2. 涓哄墿浣欏姩鎬?ASM 杈圭晫琛ユ洿娓呮櫚鐨勮嚜鍔ㄥ寲鍥炲綊锛屽挨鍏舵槸 early path 瀛楄妭鐮佸畨鍏ㄤ笌澹版槑鏂规硶鎺㈡祴銆?3. 鍙湪纭鏀剁泭鏄庢樉楂樹簬椋庨櫓鏃讹紝鎵嶇户缁墛鍑忓姩鎬?ASM銆?
