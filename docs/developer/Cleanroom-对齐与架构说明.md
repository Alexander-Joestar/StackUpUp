# Cleanroom 瀵归綈涓庢灦鏋勮鏄?

## 鐩爣

鏈枃闈㈠悜寮€鍙戣€咃紝璁板綍 StackUpUp 鍦?`Minecraft 1.12.2` 涓婁笌 `CleanroomMC` 鐢熸€佸榻愭椂鐨勫綋鍓嶅伐绋嬪垽鏂€?

鏍稿績鐩爣锛?

1. 淇濇寔 Kotlin 浠ｇ爜涓诲锛屽噺灏戞棫寮?Java/ASM 渚靛叆銆?
2. 鎶婂浐瀹氱洰鏍囪ˉ涓侀€愭杩佸埌 `MixinBooter + Mixin`銆?
3. 鐢ㄧ粺涓€鐨?`ItemStack` 瑙勫垯鍐呮牳瑙ｅ喅 metadata 鐗╁搧鍏煎锛岃€屼笉鏄负鍗曚釜妯＄粍鍐欑壒鍒ゃ€?

## 褰撳墠鏋舵瀯缁撹

### 1. 瑙勫垯鍐呮牳浼樺厛缁熶竴

杩愯鏃跺爢鍙犳眰鍊煎凡缁忕粺涓€鍒帮細

1. `StackContext`
2. `StackContextResolver`
3. `StackLimitService`

杩欐潯閾捐矾鍙叧蹇冭鍒欑湡姝ｉ渶瑕佺殑鏈€灏忎俊鎭細

1. `itemId`
2. `modId`
3. `metadata`
4. `type`
5. `baseLimit`
6. `oreNames`

杩欐牱鍋氱殑濂藉鏄細

1. 閬垮厤鍚勮ˉ涓佺偣鍚勮嚜鎷兼帴 metadata 閫昏緫銆?2. 璁?GT銆佺熆杈炪€佹櫘閫?metadata 鐗╁搧璧板悓涓€濂椾笂灞傝涔夈€?3. 璁╃紦瀛樼矑搴﹀拰瑙勫垯璇箟淇濇寔涓€鑷淬€?4. 璁?`size > 2` 涓€绫昏鍒欑ǔ瀹氫綔鐢ㄤ簬鈥滃師濮嬪熀绾库€濓紝鑰屼笉鏄伓鍙戠粦瀹氬埌褰撳墠鍫嗗彔鏁伴噺銆?
### 1.1 ItemHandler / Slot 缁熶竴璇箟

褰撳墠涓婚摼宸茬粡缁熶竴涓轰袱灞傚垽鏂細

1. `Slot` 浣撶郴锛?   `StackLimitHooks.resolveDynamicSlotLimit`
2. `SlotItemHandler` 鐗规畩璺緞锛?   `StackLimitHooks.resolveItemHandlerSlotLimit`

涔嬫墍浠ュ崟鐙繚鐣欑浜屾潯锛屼笉鏄负浜嗙粰 Forge 鐗瑰垽锛岃€屾槸鍥犱负 `SlotItemHandler#getItemStackLimit`
鍐呴儴浼氬厛澶嶅埗杈撳叆鏍堬紝鍐嶇敤 `stack.getMaxStackSize()` 鍋氭ā鎷熸彃鍏ャ€?
鍦?DSL 瀛樺湪 `size > 2 -> ...` 杩欑被瑙勫垯鏃讹紝妯℃嫙鏍堢殑 `baseLimit` 浼氳鍐嶆鏀惧ぇ銆?濡傛灉杩欓噷鍙仛鈥淩ETURN 鍚庢寜杩斿洖鍊煎厹搴曗€濓紝灏变細鍑虹幇锛?
1. 妲戒綅涓婇檺姝ｇ‘
2. 鐗╁搧涓婇檺琚敊璇斁澶?3. 宸﹂敭鎻愬彇 / 鏀剧疆鏁伴噺涓庣湡瀹炴Ы浣嶈兘鍔涜劚鑺?
鍥犳杩欓噷鐨勬纭仛娉曟槸锛?
1. 淇濈暀杈撳叆鏍堢殑鐪熷疄瑙勫垯涓婇檺
2. 鍚屾椂璇诲彇鐪熷疄 `getSlotStackLimit()`
3. 鏈€缁堢粨鏋滀互妲戒綅鐪熷疄涓婇檺涓虹‖涓婄晫

### 2. 鍥哄畾鐩爣浼樺厛杩佸埌 Mixin

褰撳墠宸茶縼鍒?`late mixin` 鐨勬ā缁勫浐瀹氱洰鏍囧寘鎷細

1. Mantle
2. IC2
3. AE2
4. Actually Additions
5. Refined Storage / MoreRefinedStorage 宸茬煡鍥哄畾璺緞
6. CyclopsCore 搴撳瓨鍩虹被 `SimpleInventory`

杩欓噷鐨勫垽鏂爣鍑嗗緢绠€鍗曪細

1. 绫诲悕宸茬煡
2. 鏂规硶绛惧悕绋冲畾
3. 瀛樺湪鏄庣‘鐨勮嚜鍔ㄥ寲鍥炲綊鍏ュ彛

婊¤冻杩欎笁鐐癸紝灏变紭鍏堢敤 mixin锛岃€屼笉鏄户缁妸閫昏緫鍫嗚繘 `DynamicCompatTransformer`銆?
褰撳墠鏃╂湡涓婚摼涓紝浠ヤ笅鏍稿績 mixin 涔熷凡杩佸埌 Java锛?
1. `ContainerMixin`
2. `SlotLimitMixin`
3. `SlotItemHandlerMixin`
4. `ForgeItemHandlerLimitMixin`
5. `ItemMixin`
6. `ItemStackMixin`

褰撳墠绾︽潫琛ュ厖锛?
1. 鎵€鏈?Mixin 婧愮粺涓€鏀惧湪 `src/main/java`
2. Kotlin 缁х画鎵挎媴瑙勫垯鍐呮牳銆佽繍琛屾椂 facade銆佽嚜鍔ㄥ寲涓庤緟鍔╅€昏緫
3. 涓嶅啀鏂板 Kotlin Mixin锛岄伩鍏?1.12.2 娉ㄨВ澶勭悊涓庢贩娣嗘槧灏勯摼璺嚭鐜伴澶栧櫔闊?
### 3. 鐪熷姩鎬佺洰鏍囨殏淇濈暀鏈€灏?ASM

褰撳墠浠嶄繚鐣?ASM 鐨勫尯鍩熶富瑕佹槸锛?

1. 鍔ㄦ€佸彂鐜扮殑 `IInventory`
2. 鍔ㄦ€佸彂鐜扮殑 `IItemHandler`
3. 鍔ㄦ€佸彂鐜扮殑 `Slot`
5. 灏戦噺寮€鍙戝吋瀹?alias 琛ヤ竵

鍘熷洜涓嶆槸鈥滄棫浠ｇ爜鎳掑緱鏀光€濓紝鑰屾槸杩欎簺杈圭晫纭疄鏇撮€傚悎璋ㄦ厧澶勭悊锛?

1. 鐩爣闆嗗悎杩愯鏃跺喅瀹?
2. 闈欐€佸垪涓?mixin 瀹规槗婕忕綉
3. 鐩稿叧璺緞涓€鏃︽墦鍋忥紝鍏煎鍥炲綊鐨勭垎鐐搁潰浼氭瘮鐜板湪鏇村ぇ

## 閰嶇疆灞傚喅绛?

褰撳墠閰嶇疆灞傚凡缁忚縼鍒?`Forge 1.12.2 @Config` 娉ㄨВ椹卞姩銆?

褰撳墠绛栫暐锛?

1. `@Config` 璐熻矗閰嶇疆澹版槑銆佽寖鍥淬€侀噸鍚姹備笌璇█閿€?
2. `StackUpUpConfig` 鍚屾椂缁х画浣滀负杩愯鏃?facade锛岄伩鍏嶆妸閰嶇疆妯″瀷鎵╂暎鍒颁笟鍔″眰銆?
3. `ConfigGui` 缁х画淇濈暀锛屼絾搴曞眰鏁版嵁婧愬凡鍒囧埌娉ㄨВ閰嶇疆绫伙紝鑰屼笉鏄棫 `Configuration` 鎵嬪伐鎷艰銆?
4. 涓昏鍒欑洰褰曞浐瀹氫负 `config/stackupup/`
5. 褰撳墠榛樿涓荤紪杈戞枃浠朵负 `config/stackupup/main.su`

杩欐牱鍋氱殑鐩殑涓嶆槸鍗曠函鈥滄崲鎴愭敞瑙ｂ€濓紝鑰屾槸锛?

1. 璁╅厤缃畾涔夊拰鍏冩暟鎹泦涓湪涓€澶勩€?
2. 淇濈暀鐜版湁杩愯鏃朵唬鐮佺殑绋冲畾璇诲彇鎺ュ彛銆?
3. 鎶婄敤鎴峰彲瑙佺殑 `stackup` 閰嶇疆鏍囪瘑缁熶竴鏀跺彛鍒?`stackupup`銆?

## DSL 璁捐鍐崇瓥

褰撳墠 DSL v2 鐨勮璁℃柟鍚戞槸鈥滄櫘閫氱帺瀹跺彲鍐欙紝浣嗗張涓嶄細鏃犻檺澶嶆潅鍖栤€濄€?

宸茶惤瀹炵殑鍏抽敭鐐癸細

1. 鍏抽敭璇嶄娇鐢?`item` / `mod` / `ore` / `meta` / `metadata` / `size`
2. 鏀寔 `item = modid:path:meta`
3. 鍏煎 `item = modid:path@meta`
4. 鏀寔 `item in [...]`
5. 鏀寔 `&&` 涓?`||`
6. 鍥哄畾浼樺厛绾т负 `&& > ||`
7. 鏆備笉鏀寔鎷彿
8. 鍔ㄤ綔缁熶竴涓?`->` 姝ヨ繘閾撅紝绀轰緥锛歚size > 64 -> *2 -> +10`

杩欏闄愬埗鏄埢鎰忕殑锛氬畠璁╄〃杈惧紡瓒冲寮猴紝浣嗕笉鑷充簬鑶ㄨ儉鎴愯剼鏈瑷€銆?
### DSL 浣滀负缁熶竴鏍煎紡锛屼笉浣滀负鍞竴鏉ユ簮

杩欓噷寤鸿灏芥棭鍥哄畾涓€鏉¤竟鐣岋細

1. DSL 鏄澶栧敮涓€鐨勮鍒欒〃杈炬牸寮?2. 鏂囦欢涓嶆槸鍞竴瑙勫垯鏉ユ簮

涔熷氨鏄锛屽悗缁棤璁烘潵鑷細

1. `config/stackupup/*.su`
2. `<save>/data/stackupup/world.su`
3. `/stackupup run "..."`
4. CraftTweaker / GroovyScript / FTB Quests / Better Questing 涔嬬被澶栭儴妗ユ帴

鏈€缁堥兘搴斿厛杩涘叆鍚屼竴绉嶁€滆鍒欐簮鈥濇娊璞★紝鍐嶇粺涓€缂栬瘧鎴?`RuleSnapshot`銆?
鎺ㄨ崘鐨勬潵婧愬垎灞傦細

1. `file`
   鐢ㄦ埛鎸佷箙閰嶇疆锛岄粯璁や富鏉ユ簮
2. `session`
   涓存椂鍛戒护娉ㄥ叆锛屽彧鍦ㄥ綋鍓嶆父鎴忎細璇濇湁鏁?3. `external:<sourceId>`
   澶栭儴妯＄粍鎴栬剼鏈ˉ鎺ョ殑鍛藉悕鏉ユ簮锛屽彲鏇挎崲銆佸彲娓呯┖锛岄粯璁や笉钀界洏

杩欐牱鍋氱殑濂藉鏄細

1. 鐢ㄦ埛濮嬬粓鍙涓€濂?DSL
2. 杩愯鏃朵笉闇€瑕佸尯鍒嗏€滄枃浠惰鍒欒娉曗€濆拰鈥滄帴鍙ｈ鍒欒娉曗€?3. 涓嶄細鍥犱负鍛戒护鎴栦换鍔＄郴缁熺殑涓存椂娉ㄥ叆姹℃煋涓昏鍒欐枃浠?4. 鍚庣画瑕佸仛 `stackupup.run("...")` 鎴栨ˉ鎺?API 鏃讹紝鍙槸鏂板鏉ユ簮锛屼笉鏄柊澧炶娉?
褰撳墠璺緞淇涔熷凡缁忓洿缁曡繖涓垽鏂仛浜嗙涓€姝ユ敹鍙ｏ細

1. 瑙勫垯鏂囦欢瀹氫綅鍥哄畾鍒?`FMLPreInitializationEvent.modConfigurationDirectory`
2. 瀹㈡埛绔娆¤繘鍏ヤ笘鐣屾椂浼氳ˉ鍙戞渶杩戜竴娆¤鍒欏姞杞介敊璇?3. `reload` 閿欒鍚屾椂杩涘叆鏃ュ織鍜岃亰澶╂爮
4. 缂哄け鐨?`config/stackupup/main.su` 浼氳嚜鍔ㄥ垱寤烘ā鏉?5. 鏈哄櫒鍐欏叆鐨勪笘鐣岃鍒欏綋鍓嶆敹鏁涘埌 `<save>/data/stackupup/world.su`
6. 鏈哄櫒鍐欏叆鎸夊懡鍚嶅潡鏇挎崲锛屼笉鍋氭櫤鑳藉綊骞讹紝浼樺厛淇濊瘉瑙勫垯鏉ユ簮涓庤鐩栭『搴忔竻鏅?
## 鍥炲綊绛栫暐

涓诲洖褰掑叆鍙ｅ浐瀹氫负锛?

```powershell
.\gradlew.bat runServerAutoTestMatrix
```

瀹冧笉鏄畝鍗曠殑鈥滆兘鍚姩灏辩畻杩団€濓紝鑰屾槸楠岃瘉锛?

1. 瑙勫垯姹傚€?
2. 鐪熷疄 `ItemStack` 涓婇檺
3. 鎻掓Ы涓婇檺
4. GT metadata 鐗╁搧鐭╅樀

闄よ繖鏉′富鍥炲綊澶栵紝杩樹繚鐣欙細

1. `MixinBooterIntegrationTest`
2. `ItemStackPatchTest`
3. `MaxStackConstantPatchTest`

鏂板鐨勫閮ㄥ吋瀹规帰閽堬細

1. `colossalchests_inventory_limit`
   褰撳墠宸插湪鐪熷疄 `CyclopsCore + ColossalChests` jar 鐜涓嬮€氳繃
2. `refinedstorage_storage_monitor_extract`
   宸叉帴鍏ュ弽灏勬帰閽堬紝绛夊緟寮曞叆 `refinedstorage` 鎴?`MoreRefinedStorage` 鏍锋湰鍚庤嚜鍔ㄦ墽琛?

## 2026-04-20 Core 收缩补充

1. `DynamicCompatTransformer` 现已收敛为入口 adapter，不再单独持有补丁计划构建层。
2. `CompatibilityLimitPatch.planFor(...)` 成为动态兼容补丁的唯一决策入口。
3. `FixedCompatTargets` 采用单表声明，`contains`、`all`、`probeTargets` 均从同一张表派生。
4. `ClassHierarchyRepository` 仅暴露父类与接口查询，层级元数据已私有化。

## 涓嬩竴姝ュ缓璁?

1. 缁х画鍑忓皯 `DynamicCompatTransformer` 涓粛灞炲浐瀹氱洰鏍囩殑鍘嗗彶琛ヤ竵銆?
2. 涓哄姩鎬?ASM 杈圭晫琛ユ洿鏄庣‘鐨勨€滀负浠€涔堣繕淇濈暀鈥濈殑娉ㄩ噴涓庢祴璇曘€?
3. DSL 瑙ｆ瀽宸插垏鍒?tokenizer 椹卞姩锛宍DslTokenType` 缁熶竴鎸佹湁绗﹀彿/鍏抽敭瀛楀畾涔夈€?4. 褰撳墠鏄庣‘涓嶅紩鍏ラ€氱敤 parser generator锛涜繖濂楄娉曡妯℃洿閫傚悎鏈湴闆惰繍琛屾椂渚濊禆鐨勬墜鍐欒瘝娉?閫掑綊涓嬮檷瑙ｆ瀽銆?
## 褰撳墠宸茬煡鍣煶

1. `runServerAutoTestMatrix` 浠嶄細鐪嬪埌 `Forgelin-Continuous` 鐨?multi-release jar 琚?FML 1.12.2 鎵弿鏃舵姤閿欏苟蹇界暐 `META-INF/versions/9/...` 鐨勬棩蹇椼€?
2. 褰撳墠鑷姩鍖栫煩闃典粛鐒跺彲浠ユ垚鍔熷畬鎴愶紝鍥犳杩欏睘浜庡紑鍙戠幆澧冨櫔闊筹紝涓嶆槸鏈」鐩湰杞敼鍔ㄥ紩鍏ョ殑鍔熻兘鎬у洖褰掋€?
3. 濡傛灉鍚庣画瑕佺户缁帇鏃ュ織鍣煶锛屼紭鍏堟柟鍚戝簲鏄紑鍙戣繍琛屾椂渚濊禆鏁寸悊锛岃€屼笉鏄洖閫€褰撳墠 Kotlin / Forgelin 鏂规銆?
