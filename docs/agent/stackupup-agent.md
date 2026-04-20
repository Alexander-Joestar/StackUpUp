# StackUpUp Agent Notes

## 鏍稿績浜嬪疄

1. 娓告垙鐗堟湰鍥哄畾涓?`Minecraft 1.12.2`
2. Kotlin 杩愯鏃舵潵鑷?`Forgelin-Continuous`
3. 鍥哄畾鐩爣琛ヤ竵浼樺厛璧?`MixinBooter`
4. 鐪熷姩鎬佺洰鏍囦粛淇濈暀鏈€灏?ASM

## 瑙勫垯绯荤粺

1. 涓昏鍒欑洰褰曞浐瀹氾細`config/stackupup/`
2. 涓荤紪杈戞枃浠讹細`config/stackupup/main.su`
3. 鐢ㄦ埛瑕嗙洊鏂囦欢锛歚config/stackupup/user.su`
4. 涓栫晫鎸佷箙瑙勫垯锛歚<save>/data/stackupup/world.su`
5. 鍙繚鐣?DSL v2锛屼笉鍐嶅吋瀹?DSL v1
6. 鏀寔娉ㄩ噴锛歚#`銆乣//`銆乣/* ... */`
7. DSL 鍙湪鍔犺浇鍜岄噸杞芥椂瑙ｆ瀽锛屼笉浼氬湪姣忔 `ItemStack` 鍒涘缓鏃堕噸鏂拌В鏋?8. 杩愯鏃跺彧鍋氬尮閰嶄笌缂撳瓨
9. `DslTokenType` 鏄?DSL 绗﹀彿涓庡叧閿瓧鐨勫崟涓€鍏ュ彛
10. 褰撳墠涓嶅紩鍏ラ€氱敤 parser 搴擄紝缁х画淇濇寔鏈湴鎵嬪啓 tokenizer + token 娴?parser
11. 鍔ㄤ綔璇硶鍙繚鐣?`->`锛屾敮鎸?`-> 128`銆乣-> +32`銆乣-> -16`銆乣-> *2`銆乣-> /2`
12. 鏀寔娴佸紡鍔ㄤ綔閾撅細`size > 1 -> *2 -> +10`
13. DSL 鏄粺涓€琛ㄨ揪鏍煎紡锛屼絾涓嶆槸鍞竴瑙勫垯鏉ユ簮
14. 闀挎湡瑙勫垯婧愭媶涓猴細
   鏂囦欢瑙勫垯 `file`
   涓存椂鍛戒护瑙勫垯 `session`
   澶栭儴鎺ュ彛瑙勫垯 `external:<sourceId>`
15. 鍛戒护鎴栧閮ㄦ帴鍙ｇ敓鎴愮殑瑙勫垯榛樿涓嶇洿鎺ュ洖鍐欎富瑙勫垯鏂囦欢
16. 瀹㈡埛绔娆¤繘涓栫晫浼氳ˉ鍙戞渶杩戜竴娆¤鍒欏姞杞介敊璇笌澶嶆潅搴︽彁閱?17. `WorldRuleStore` 浼氭妸澶栭儴鏉ユ簮鍐欐垚鍛藉悕鍧楋紝骞舵寜鏉ユ簮鏁村潡鏇挎崲
18. 褰撳墠涓栫晫鎸佷箙鍖栦笉鍋氭櫤鑳藉綊骞讹紝浼樺厛淇濊瘉鍙浛鎹€佸彲璇汇€佸彲杩借釜

## 宸叉敮鎸?DSL 閲嶇偣

1. `item = modid:path`
2. `item = modid:path:meta`
3. `item = modid:path@meta`
4. `item in [...]`
5. `meta` 涓?`metadata`
6. `size in [...]`
7. `2 < size < 64`
8. `&& > ||`

## 鍏抽敭璇箟

1. 绮剧‘鐗╁搧涓婇檺鍏ュ彛锛歚StackLimitHooks.applyDynamicStackLimit`
2. 鍏煎涓婇檺鍏ュ彛锛歚StackLimitHooks.getCompatibilityStackSize`
3. 涓嶈娣锋穯鈥滆鍒欎笂闄愨€濆拰鈥滃吋瀹瑰父閲忎笂闄愨€?4. `size` 鍦?DSL 涓〃绀?`baseLimit`锛屼笉鏄?`ItemStack.count`
5. `SlotItemHandler#getItemStackLimit` 蹇呴』鍚屾椂鐪嬶細
   `stack.maxStackSize`
   `getSlotStackLimit()`
   鍙湅杩斿洖鍊煎悗淇浼氭紡鎺?`size > 2` 杩欑被鎶婅緭鍏ユ爤涓婇檺鏀惧ぇ鐨勮鍒?
## 娓叉煋鐑矾寰?
1. `StackCountTextLayout` 灞炰簬楂橀璺緞锛屼紭鍏堢函鏁存暟杩愮畻
2. 鍏堝幓鏍煎紡鐮侊紝鍐嶅仛 `toIntOrNull()`锛岄伩鍏?`搂` 骞叉壈瑙ｆ瀽
3. 涓嶈鍦ㄨ繖閲岄噸鏂板紩鍏?`String.format`
4. 鑻ュ繀椤诲仛鏈湴鍖栨棤鍏虫牸寮忓寲锛岀粺涓€鏄惧紡浣跨敤 `Locale.ROOT`

## 涓诲洖褰?
1. `.\gradlew.bat --no-daemon runServerAutoTestMatrix`
2. 閲嶇偣鍏虫敞锛歚瑙ｆ瀽`銆乣瀹為檯`銆乣鎻掓Ы`
3. 褰撳墠鐭╅樀锛?   `gregtech:meta_ingot@324`
   `gregtech:meta_plate@324`
   `gregtech:meta_dust@324`
   `gregtech:meta_item_1@516`
4. `slot_item_handler_limit` 鐜板湪鏄‖鍥炲綊椤癸紝涓嶈兘鍐嶉€€鍖栦负 `鐗╁搧涓婇檺=102400`

## Mixin 绾︽潫

1. 鎵€鏈?Mixin 婧愭枃浠剁粺涓€鏀惧湪 `src/main/java`
2. 涓嶅啀鏂板 Kotlin Mixin
3. Kotlin 浠ｇ爜璐熻矗瑙勫垯銆佽繍琛屾椂銆佽嚜鍔ㄥ寲涓庝笟鍔￠€昏緫

## 宸茶縼绉荤殑 RS 鍥哄畾鐩爣

1. `com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler`
2. `com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandlerPortable`
3. `com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeStorageMonitor`

瀹冧滑鐜板湪璧?`late mixin`锛屼笉鍐嶈蛋鏃?RS 鍓嶇紑 ASM銆?
## 褰撳墠淇濈暀鐨?ASM 杈圭晫

1. 鍔ㄦ€佸彂鐜扮殑 `IInventory`
2. 鍔ㄦ€佸彂鐜扮殑 `IItemHandler`
3. 鍔ㄦ€佸彂鐜扮殑 `Slot`
5. 灏戦噺寮€鍙戝吋瀹?alias 琛ヤ竵

## 宸叉竻鐞嗙殑閬楃暀鏂囦欢

浠ヤ笅鏃?ASM 鏂囦欢宸插垹闄わ紝涓嶈鍐嶆妸瀹冧滑褰撲綔褰撳墠璺緞锛?

1. `NetHandlerPlayServerPatch.kt`
2. `PacketBufferWriterSplice.kt`
3. `PacketUtilWriterSplice.kt`
4. `RenderEntityItemPatch.kt`
5. `RenderEntityItemSplice.kt`
6. `RenderItemPatch.kt`

## 宸茬煡寮€鍙戝櫔闊?

1. `Forgelin-Continuous` 鍦?FML 1.12.2 涓嬩細鍑虹幇 multi-release jar 鎵弿鍛婅
2. 褰撳墠 `runServerAutoTestMatrix` 鎴愬姛鏃跺彲瑙嗕负鍔熻兘鏈彈褰卞搷

## 鏂板澶栭儴鍏煎鎺㈤拡

1. `colossalchests_inventory_limit`
   宸茬敤鐪熷疄 `CyclopsCore + ColossalChests` jar 鍥炲綊閫氳繃
2. `refinedstorage_storage_monitor_extract`
   宸叉帴鍏ュ弽灏勬帰閽?   褰撳墠浠呭湪 `refinedstorage` 鎴栧吋瀹?fork 瀹為檯鍔犺浇鏃惰嚜鍔ㄨ繍琛?3. `combined_inv_wrapper_limit`
4. `inv_wrapper_limit`
5. `ranged_wrapper_limit`
6. `sided_inv_wrapper_limit`
7. `slot_item_handler_limit`

鍏朵腑 `SimpleInventory / SlotItemHandler / InvWrapper / SidedInvWrapper / CombinedInvWrapper / RangedWrapper`
杩欑粍 fixed target 鐨?probe 瑕嗙洊鐜板湪鐢?`FixedCompatTargets.probeTargets()` 瀵归綈锛屾柊澧炴垨绉诲嚭鍥哄畾鐩爣鏃讹紝涓嶈鍐嶅彧鏀逛竴渚у悕鍗曘€?
## 鍛戒护涓庨厤缃?

1. 鍛戒护锛歚/stackupup reload`銆乣/stackupup edit`
2. Tooltip 妯″紡锛歚off / always / advanced`
3. 瑙勫垯澶嶆潅搴︽彁閱掞細鍙紑鍏?
4. 涓嶅厑璁歌嚜瀹氫箟瑙勫垯鏂囦欢鍚?
5. 閰嶇疆澹版槑灞傚凡杩佸埌 `Forge @Config`
