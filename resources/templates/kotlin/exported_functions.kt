package com.x48lab.hackcraft2.infrastructure.entity

import com.x48lab.hackcraft2.HackCraft2
import com.x48lab.hackcraft2.application.event.BlockChangedEvent
import com.x48lab.hackcraft2.application.event.EntityChangedEvent
import com.x48lab.hackcraft2.application.util.WorldGuardHelper
import com.x48lab.hackcraft2.domain.entity.ControllableEntity
import com.x48lab.hackcraft2.domain.interfaces.EntityDataStore
import com.x48lab.hackcraft2.domain.interfaces.ScriptExecutor
import com.x48lab.hackcraft2.domain.interfaces.toProxyObject
import com.x48lab.hackcraft2.domain.model.EventData
import com.x48lab.hackcraft2.domain.repository.EntityRepository
import com.x48lab.hackcraft2.domain.repository.ScriptRepository
import com.x48lab.hackcraft2.domain.type.Volume
import com.x48lab.hackcraft2.domain.type.mapToBlockData
import com.x48lab.hackcraft2.domain.usecase.ScriptService
import com.x48lab.hackcraft2.domain.usecase.WebHandlerService
import com.x48lab.hackcraft2.domain.usecase.WebHandlerType
import com.x48lab.hackcraft2.domain.util.*
import com.x48lab.hackcraft2.infrastructure.util.TaskRunnerImpl
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.data.*
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Chest
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Slab
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.*
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.metadata.MetadataValue
import org.bukkit.util.Vector
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.koin.java.KoinJavaComponent
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * システム関連の機能を提供するクラス
 * @property name "system" - グローバル定数として使用される名前
 */
@HostAccess.Object(name = "system")
class HackSystemImpl : HackSystem() {
  private val _taskRunner = TaskRunnerImpl()
  private val openAIService: OpenAIService by KoinJavaComponent.inject(OpenAIService::class.java)
  private val materialService: MaterialService by KoinJavaComponent.inject(MaterialService::class.java)

  /**
   * GPTを使用してテキストを生成します
   * @param prompt プロンプト文字列
   * @return 生成されたテキスト
   */
  @HostAccess.Export
  override fun gpt(prompt: String): String {
      return _taskRunner.executeWithResult<String>({
          return@executeWithResult openAIService.chatCompletion(prompt)
      }, 0).get()
  }

  /**
   * 指定されたURLからデータを取得します
   * @param url 取得先のURL
   * @return 取得したデータ（テキストまたはバイナリ）
   */
  @HostAccess.Export
  override fun fetch(url: String): Any {
      return _taskRunner.executeWithResult<Any>({
          val client = HttpClient.newHttpClient()
          val request = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .build()

          val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
          val bodyStream: InputStream = response.body()
          val contentType: String? = response.headers().firstValue("Content-Type").orElse(null)

          if (contentType != null && contentType.startsWith("text")) {
              // テキストデータの場合は文字列として返す
              return@executeWithResult bodyStream.bufferedReader().use { it.readText() }
          } else {
              // 画像などのバイナリデータの場合はバイト配列として返す
              return@executeWithResult bodyStream.readAllBytes()
          }
      }, 0).get()
  }

  @HostAccess.Export
  override fun fetchText(url: String, encoding: String): String {
      return _taskRunner.executeWithResult<String>({
          val client = HttpClient.newHttpClient()
          val request = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .build()

          val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
          val bodyStream: InputStream = response.body()
          val contentType: String? = response.headers().firstValue("Content-Type").orElse(null)

          if (contentType != null && contentType.startsWith("text")) {
              // テキストデータの場合はバイト配列として取得し、指定されたエンコーディングで文字列に変換する
              val bytes = bodyStream.readAllBytes()
              return@executeWithResult String(bytes, Charset.forName(encoding))
          } else {
              throw IllegalStateException("Unsupported content type: $contentType")
          }
      }, 0).get()
  }

  @HostAccess.Export
  override fun findNearestBlock(color: String): Map<String, Any?> {
      val rgb = getRGBHex(color)
      if (rgb == null) {
          return mapOf(
              "name" to "minecraft:air"
          )
      }

      val nearestBlockName = ColorTable().getItemStack(rgb)

      return mapOf(
          "name" to (nearestBlockName ?: "minecraft:air")
      )
  }

  private fun getRGBHex(input: String): Int? {
      val hexPattern = "^#?([0-9A-Fa-f]{6})$".toRegex()

      return if (input.matches(hexPattern)) {
          val hex = input.removePrefix("#")
          val r = hex.substring(0, 2).toInt(16)
          val g = hex.substring(2, 4).toInt(16)
          val b = hex.substring(4, 6).toInt(16)

          val rgb = r shl 16 or (g shl 8) or b
          return rgb
      } else {
          val num = try {
              input.toDouble().toInt()
          } catch (e: NumberFormatException) {
              e.printStackTrace()
              null
          }

          return num
      }
  }
}

/**
 * エンティティの制御機能を提供するクラス
 * @property name "entity" - グローバル定数として使用される名前
 */
@HostAccess.Object(name = "entity,crab")
abstract class ControllableEntityImpl internal constructor(entity: LivingEntity) : ControllableEntity(entity) {
    companion object {
        private const val SCRIPT_EXECUTOR_KEY = "scriptExecutor"
        private const val WAITING_REDSTONE_CHANGED_FUTURE_KEY = "waitingChangedFuture"
        private const val WAITING_PLAYER_CHAT_FUTURE_KEY = "waitingPlayerChatFuture"
        private const val WAITING_BLOCK_BREAK_FUTURE_KEY = "waitingBlockBreakFuture"
    }

    private val _entityDataStore: EntityDataStore by KoinJavaComponent.inject(EntityDataStore::class.java)
    private val _entityRepository: EntityRepository by KoinJavaComponent.inject(EntityRepository::class.java)
    private val _scriptRepository: ScriptRepository by KoinJavaComponent.inject(ScriptRepository::class.java)
    private val _scriptService: ScriptService by KoinJavaComponent.inject(ScriptService::class.java)
    private val webHandlerService: WebHandlerService by KoinJavaComponent.inject(WebHandlerService::class.java)
    private val _worldGuard: WorldGuardHelper by KoinJavaComponent.inject(WorldGuardHelper::class.java)
    private val server: Server by KoinJavaComponent.inject(Server::class.java)
    private var _eventArea: Volume? = null
    private var _eventHandlers: MutableMap<String, Value> = mutableMapOf()

    private var _waitingChangedFuture: CompletableFuture<Map<String, Any>>? = null
    var waitingRedstoneChangedFuture: CompletableFuture<Map<String, Any>>?
        get() = getMetadata(WAITING_REDSTONE_CHANGED_FUTURE_KEY) as? CompletableFuture<Map<String, Any>>
        set(value) {
            setMetadata(WAITING_REDSTONE_CHANGED_FUTURE_KEY, value)
            _waitingChangedFuture = value
        }
    var waitingPlayerChatChangedFuture: CompletableFuture<Map<String, Any>>?
        get() = getMetadata(WAITING_PLAYER_CHAT_FUTURE_KEY) as? CompletableFuture<Map<String, Any>>
        set(value) {
            setMetadata(WAITING_PLAYER_CHAT_FUTURE_KEY, value)
            _waitingChangedFuture = value
        }

    var messageEventHandler: Value?
        get() = getMetadata("messageEventHandler") as Value?
        set(value) = setMetadata("messageEventHandler", value)

    var scriptExecutor: ScriptExecutor?
        get() = getMetadata(SCRIPT_EXECUTOR_KEY) as? ScriptExecutor
        set(value) = setMetadata(SCRIPT_EXECUTOR_KEY, value)

    private val _taskRunner = TaskRunnerImpl()
    private val _inventory = EntityInventory(this)
    private var _selectedSlotNo = 0
    private var _entityUtil = EntityUtil(entity)
    private var _saveArea: SaveArea? = null
    private var maxMoveSpeed: Double = 20.0   // エンティティの最大速度
    private var maxStamina: Double = 200.0   // エンティティの最大スタミナ
    private var stamina: Double = maxStamina      // エンティティのスタミナ
    private var lastRecoveryTime: Long = System.currentTimeMillis() // 最後にスタミナが回復された時刻
    private var mySender = Bukkit.getConsoleSender()
    private val eventList = ArrayDeque<Map<String, Any?>>()

    private fun recoverStamina(amount: Int) {
        stamina += amount
        if (stamina > maxStamina) stamina = maxStamina  // スタミナの最大値を超えないように調整
    }

    private fun calculateStaminaConsumption(speed: Double): Double {
        // 例: 基本消費量は2とし、速度が1増えるごとに消費量を1増やす
        return 2 + (speed - 1)
    }

    init {
        reload()
    }

    private fun getMetadata(key: String): Any? {
        val metadataList: List<MetadataValue> = entity.getMetadata(key)
        for (metadata in metadataList) {
            if (metadata.owningPlugin == HackCraft2.instance) {
                return metadata.value()
            }
        }
        return null
    }

    private fun setMetadata(key: String, value: Any?) {
        if (value != null) {
            entity.setMetadata(key, FixedMetadataValue(HackCraft2.instance, value))
        } else {
            entity.removeMetadata(key, HackCraft2.instance)
        }
    }

    private fun updateLocation(location: Location) {
        val oldLocation = entity.location.clone()
        entity.teleport(location)
        server.pluginManager.callEvent(EntityChangedEvent(entity, oldLocation, location))
    }

    private fun updateRotation(yaw: Float, pitch: Float) {
        val oldLocation = entity.location.clone()
        entity.setRotation(yaw, pitch)
        val newLocation = entity.location.clone()
        newLocation.yaw = yaw
        newLocation.pitch = pitch
        server.pluginManager.callEvent(EntityChangedEvent(entity, oldLocation, newLocation))
    }

    private fun placeBlock(location: Location, itemStack: ItemStack, side: String) {
        val block = entity.world.getBlockAt(location)
        val blockData = Bukkit.createBlockData(itemStack.type)
        var blocks = mutableListOf(block)
        // backup
        _saveArea?.addChangeBlocks(blocks)

        block.type = Material.AIR // 一度空気にする
        block.blockData = blockData

        // 向きを調整
        val face = BlockUtil.getBlockDirection(location)
        if (blockData is Directional) {
            blockData.facing = when (side.lowercase()) {
                "right" -> when (face) {
                    BlockFace.NORTH -> BlockFace.EAST
                    BlockFace.SOUTH -> BlockFace.WEST
                    BlockFace.EAST -> BlockFace.SOUTH
                    BlockFace.WEST -> BlockFace.NORTH
                    else -> face
                }

                "left" -> when (face) {
                    BlockFace.NORTH -> BlockFace.WEST
                    BlockFace.SOUTH -> BlockFace.EAST
                    BlockFace.EAST -> BlockFace.NORTH
                    BlockFace.WEST -> BlockFace.SOUTH
                    else -> face
                }

                "front" -> face
                "back" -> face.oppositeFace
                else -> face // デフォルトはそのままの向き
            }
            block.blockData = blockData
        }

        // 原木等の向きを設定
        if (blockData is Orientable) {
            val orientable = blockData as Orientable
            orientable.axis = when (side.lowercase()) {
                "front", "back" -> when (face) {
                    BlockFace.NORTH, BlockFace.SOUTH -> Axis.Z
                    BlockFace.EAST, BlockFace.WEST -> Axis.X
                    else -> Axis.Y
                }

                "left", "right" -> when (face) {
                    BlockFace.NORTH, BlockFace.SOUTH -> Axis.X
                    BlockFace.EAST, BlockFace.WEST -> Axis.Z
                    else -> Axis.Y
                }

                else -> Axis.Y // デフォルトはY軸
            }
            block.blockData = orientable
        }

        if (block.blockData is Slab) {
            val slab = block.blockData as Slab
            slab.type = when (side.lowercase()) {
                "top" -> Slab.Type.TOP
                "bottom" -> Slab.Type.BOTTOM
                else -> Slab.Type.BOTTOM // デフォルトは両方
            }
            block.blockData = slab
        } else if (block.blockData is Door) {
            val door = block.blockData as Door
            door.half = Bisected.Half.TOP
            val upperBlock: Block = block.getRelative(BlockFace.UP)
            upperBlock.blockData = door
            blocks.add(upperBlock)
        } else if (block.blockData is Bed) {
            val bed = block.blockData as Bed
            bed.part = Bed.Part.HEAD
            val topFace = (blockData as Directional).facing
            val headBlock: Block = block.getRelative(topFace)
            headBlock.blockData = bed
            blocks.add(headBlock)
        } else if (block.blockData is Chest) {
            val directions = listOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
            for (direction in directions) {
                val adjacentBlock = block.getRelative(direction)
                if (adjacentBlock.type == Material.CHEST) {
                    val adjacentChest = adjacentBlock.blockData as Chest
                    if (adjacentChest.type == Chest.Type.SINGLE && adjacentChest.facing == (blockData as Directional).facing) {
                        (blockData as Chest).type = when (direction) {
                            BlockFace.NORTH -> Chest.Type.LEFT
                            BlockFace.SOUTH -> Chest.Type.RIGHT
                            BlockFace.EAST -> Chest.Type.LEFT
                            BlockFace.WEST -> Chest.Type.RIGHT
                            else -> Chest.Type.SINGLE
                        }
                        adjacentChest.type = when (direction) {
                            BlockFace.NORTH -> Chest.Type.RIGHT
                            BlockFace.SOUTH -> Chest.Type.LEFT
                            BlockFace.EAST -> Chest.Type.RIGHT
                            BlockFace.WEST -> Chest.Type.LEFT
                            else -> Chest.Type.SINGLE
                        }
                        adjacentBlock.blockData = adjacentChest
                        blocks.add(adjacentBlock)
                        break
                    }
                }
            }
            block.blockData = blockData
        }

        // Creative Modeの場合はこの処理を省く
        if (!isCreativeMode) {
            // ItemStackの減算処理
            _inventory.removeItem(itemStack)
            BlockUtil.playBlockPlaceSound(entity, block)
        }

        val blockChangedEvent = BlockChangedEvent(location, blocks)
        server.pluginManager.callEvent(blockChangedEvent)
    }

    private fun _setBlock(x: Int, y: Int, z: Int, cord: String, block: String, side: String): Boolean {
        val itemName =
            if (block.startsWith("minecraft:")) block.substring(10) else if (block.isNotEmpty()) block else "air"
        val material = Material.getMaterial(itemName.uppercase())
        if (material == null) {
            Bukkit.getLogger().info("Material not found: $block")
            return false
        }
        val itemStack = ItemStack(material)
        if (!canBuild(entity.location)) {
            return false
        }

        val location = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
        //ブロックがおける場所かどうかチェック
        if (itemStack.type.isBlock) {
            placeBlock(location, itemStack, side)
            return true
        } else if (ItemStackUtil.isAttachableBlock(itemStack)) {
            val targetBlock = location.block
            val blockFace = _entityUtil.getClosestBlockFace(targetBlock.location, entity.location).oppositeFace

            if (blockFace == BlockFace.UP) {
                return false
            } else if (blockFace == BlockFace.DOWN) {
                val blockData = itemStack.type.createBlockData()
                val placeLocation = targetBlock.getRelative(blockFace.oppositeFace)
                if (blockData is FaceAttachable) {
                    blockData.attachedFace = FaceAttachable.AttachedFace.FLOOR
                    stateBlockChange(placeLocation, blockData)
                } else {
                    stateBlockChange(placeLocation, blockData)
                }

                return true
            } else if (!targetBlock.isPassable) {
                val compMaterial = ItemStackUtil.getWallCompatibleMaterial(itemStack, blockFace)
                val blockData = compMaterial.createBlockData()
                if (blockData is Directional) {
                    blockData.facing = blockFace
                    val placeLocation = targetBlock.getRelative(blockFace)
                    stateBlockChange(placeLocation, blockData)

                    return true
                } else if (blockData is FaceAttachable) {
                    blockData.attachedFace = FaceAttachable.AttachedFace.WALL
                    stateBlockChange(targetBlock, blockData)

                    return true
                }

                return false
            } else if (!targetBlock.getRelative(BlockFace.DOWN).isPassable) {
                val blockData = itemStack.type.createBlockData()
                if (blockData is FaceAttachable) {
                    blockData.attachedFace = FaceAttachable.AttachedFace.FLOOR
                    stateBlockChange(targetBlock, blockData)
                } else {
                    stateBlockChange(targetBlock, itemStack.type.createBlockData())
                }

                return true
            }
        }
        return false
    }

    private fun stateBlockChange(target: Block, blockData: BlockData) {
        target.blockData = blockData

        if (!isCreativeMode) {
            val sound = SoundMapping.getPlaceSound(blockData.material)
            entity.world.playSound(target.location, sound, 1f, 1f)
        }
        //backup
        _saveArea?.addChangeBlock(target)

        val blockChangedEvent = BlockChangedEvent(target.location, listOf(target))
        server.pluginManager.callEvent(blockChangedEvent)
    }

    private fun breakBlock(block: Block) {

        val location = block.location
        val blockData = block.blockData
        val blocks = BlockUtil.getRelatedBlocks(block)
        //backup
        _saveArea?.addChangeBlocks(blocks)

        // ブロックを壊した際のパーティクル（アニメーション）を表示する
        if (!isCreativeMode) {
            val itemStack = _inventory.selectItemName(_selectedSlotNo, "pickaxe") ?: ItemStack(Material.AIR)
            Bukkit.getLogger().info("itemStack: $itemStack")
            val items = block.getDrops(itemStack, entity)
            BlockUtil.playBlockBreakSound(entity, block)
            //壊したブロックのパーティクルを生成
            entity.world.spawnParticle(
                Particle.BLOCK,
                location.add(0.5, 0.5, 0.5),
                30,
                blockData
            )
            //ブロックをスポーン
            block.type = Material.AIR
            //回収
            if (items.isNotEmpty()) {
                for (item in items) {
                    val dropItems = _inventory.addItem(item)
                    for (dropItem in dropItems) {
                        entity.world.dropItemNaturally(location, dropItem.value)
                    }
                }
            }
        } else {
            BlockUtil.playBlockBreakSound(entity, block)
            //ブロックをスポーン
            entity.world.spawnParticle(
                Particle.BLOCK,
                location.add(0.5, 0.5, 0.5),
                30,
                blockData
            )
            block.type = Material.AIR;
        }

        val blockChangedEvent = BlockChangedEvent(location, blocks)
        server.pluginManager.callEvent(blockChangedEvent)

    }

    private fun canMove(location: Location): Boolean {
        return BlockUtil.canEntityMoveToLocation(location)
    }

    private val isCreativeMode: Boolean
        get() = _entityRepository.findById(uuid)?.let { namedEntity ->
            Bukkit.getPlayer(namedEntity.ownerUuid)?.let { player ->
                _worldGuard.isEntityCreativeAllowed(player, entity.location) // ここで直接結果を返す
            } ?: false // プレイヤーが存在しない場合はfalse
        } ?: false && _entityRepository.findById(entity.uniqueId)?.isCreativeMode == true

    private fun canBuild(location: Location): Boolean {
        return _entityRepository.findById(uuid)?.let { namedEntity ->
            Bukkit.getPlayer(namedEntity.ownerUuid)?.let { player ->
                _worldGuard.isEntityBuildAllowed(player, location) // ここで直接結果を返す
            } ?: false // プレイヤーが存在しない場合はfalse
        } ?: false// エンティティが存在しない場合はfalseを返す
        // return _worldGuard.isEntityBuildAllowed(location)
    }

    private fun canEnter(location: Location): Boolean {
        return _worldGuard.isEntityEnterAllowed(location)
    }

    private fun canExit(location: Location): Boolean {
        return _worldGuard.isEntityExitAllowed(location)
    }

    private fun playFailSound() {
        val sound = entity.hurtSound ?: Sound.ENTITY_PIG_AMBIENT
        entity.world.playSound(entity.location, sound, SoundCategory.NEUTRAL, 1.0f, 1.0f)
    }


    private fun moveForward(distance: Int): Boolean {
        Bukkit.getLogger().info("moveForward ${distance}");
        return _taskRunner.executeWithResult<Boolean>({
            //歩くなら座ってたらだめ
            if (entity is Sittable && entity.isSitting) {
                entity.isSitting = false
            }
            var ret = true
            val loopCount = abs(distance)
            val step = if (distance > 0) 1 else -1
            repeat(loopCount) {
                //現在の位置
                val currentLocation = entity.location
                //移動先の位置
                val targetLocation = BlockUtil.getLocation(currentLocation, 0, 0, step)
                val newTargetLocation = BlockUtil.getPassToLocation(currentLocation, targetLocation)


                if (BlockUtil.canEntityMoveToLocation(newTargetLocation)) {
                    _entityUtil.setGravity(newTargetLocation)
                    updateLocation(newTargetLocation)
                } else {
                    playFailSound()
                    _entityUtil.displayChatBubble(Messages.MOVE_ERROR)
                    return@executeWithResult false
                }
            }
            return@executeWithResult ret;
        }, period).get()
    }

    private fun moveEntityHorizontal(distance: Int): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            //歩くなら座ってたらだめ
            if (entity is Sittable && entity.isSitting) {
                entity.isSitting = false
            }
            var ret = true
            val loopCount = abs(distance)
            val step = if (distance > 0) 1 else -1
            repeat(loopCount) {
                val location = BlockUtil.getLocation(entity.location, step, 0, 0)
                val newLocation = BlockUtil.getPassToLocation(entity.location, location)
                if (BlockUtil.canEntityMoveToLocation(newLocation)) {
                    _entityUtil.setGravity(newLocation)
                    updateLocation(newLocation)
                } else {
                    playFailSound()
                    _entityUtil.displayChatBubble(Messages.MOVE_ERROR)
                    return@executeWithResult false
                }
            }
            return@executeWithResult ret;
        }, period).get()
    }

    private fun upDown(distance: Int): Boolean {
        val dt = if (abs(distance.toInt()) < 2 && entity.isOnGround) 0 else period
        return _taskRunner.executeWithResult<Boolean>({
            Bukkit.getLogger().info("upDown ${entity.velocity}")
            //歩くなら座ってたらだめ
            if (entity is Sittable && entity.isSitting) {
                entity.isSitting = false
            }
            var ret = true
            val loopCount = abs(distance.toInt())
            val step = if (distance > 0) 1 else -1
            repeat(loopCount) {
                val location = entity.location
                val currentX = location.x
                val currentY = location.y.roundToInt().toDouble()
                val currentZ = location.z
                val currentYaw = location.yaw
                val currentPitch = location.pitch

                val newX = currentX
                val newZ = currentZ
                val newY = currentY + step
                val newLocation = Location(location.world, newX, newY, newZ, currentYaw, currentPitch)
                if (BlockUtil.canEntityMoveToLocation(newLocation)) {
                    _entityUtil.setGravity(newLocation)
                    updateLocation(newLocation)
                } else {
                    playFailSound()
                    _entityUtil.displayChatBubble(Messages.MOVE_ERROR)
                    return@executeWithResult false
                }
            }
            return@executeWithResult ret
        }, dt).get()
    }

    /////////
    /// 以下、API
    override fun isEventArea(x: Int, y: Int, z: Int, cord: String): Boolean {
        if (_eventArea == null) return false
        Bukkit.getLogger().info("isEventArea $x $y $z ${_eventArea}")
        val loc = _entityUtil.newLocation(
            x.toDouble(),
            y.toDouble(),
            z.toDouble(),
            cord
        )
        return _eventArea!!.isInside(loc)
    }

    @HostAccess.Export
    override fun setEventArea(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int, cord: String) {
        val xyz1 = _entityUtil.newLocation(
            x1.toDouble(),
            y1.toDouble(),
            z1.toDouble(),
            cord
        )
        val xyz2 = _entityUtil.newLocation(
            x2.toDouble(),
            y2.toDouble(),
            z2.toDouble(),
            cord
        )
        _eventArea = Volume(xyz1, xyz2)
    }

    @HostAccess.Export
    override fun removeEventArea() {
        _eventArea = null
    }

    @HostAccess.Export
    override fun setEventHandler(event: String, handler: Value) {
        _eventHandlers[event] = handler
    }

    override fun getEventHandler(event: String): Value? {
        return _eventHandlers[event]
    }

    @HostAccess.Export
    override fun removeEventHandler(event: String) {
        _eventHandlers.remove(event)
    }

    @HostAccess.Export
    fun waitForPlayerChat(): Any? {
        return toProxyObject(waitForPlayerChatMap())
    }

    override fun waitForPlayerChatMap(): Map<String, Any> {
        val future = CompletableFuture<Map<String, Any>>().also {
            waitingPlayerChatChangedFuture = it
        }
        return future.join()
    }

    @HostAccess.Export
    fun waitForRedstoneChange(): Any? {
        return toProxyObject(waitForRedstoneChangeMap())
    }

    override fun waitForRedstoneChangeMap(): Map<String, Any> {
        val future = CompletableFuture<Map<String, Any>>().also {
            waitingRedstoneChangedFuture = it
        }
        return future.join()
    }

    override fun notifyRedStoneChanged(obj: Map<String, Any>) {
        waitingRedstoneChangedFuture?.complete(obj)
        waitingRedstoneChangedFuture = null
    }

    override fun notifyPlayerChatChanged(obj: Map<String, Any>) {
        waitingPlayerChatChangedFuture?.complete(obj)
        waitingPlayerChatChangedFuture = null
    }

    @HostAccess.Export
    override fun setOnMessage(handler: Value?) {
        messageEventHandler = handler
    }

    override fun onMessage(obj: Map<String, Any>) {
        Bukkit.getLogger().info("OnMessage $obj")
        messageEventHandler?.let {
            scriptExecutor?.call(it, obj)
        }
    }

    override fun clearListeners() {
        Bukkit.getLogger().info("clearListeners")
        waitingRedstoneChangedFuture?.completeExceptionally(InterruptedException("Plugin disabled"))
        waitingRedstoneChangedFuture = null
        waitingPlayerChatChangedFuture?.completeExceptionally(InterruptedException("Plugin disabled"))
        waitingPlayerChatChangedFuture = null
    }

    override fun openInventory(player: Player) {
        player.openInventory(_inventory.instance)
    }

    override fun closeInventory() {
        _entityRepository.findById(uuid)?.let { namedEntity ->
            _entityDataStore.saveData(namedEntity)
        }
    }

    override fun saveConfig(file: File) {
        val config = YamlConfiguration()
        _inventory.save(config)
        config.save(file)
    }

    override fun reload() {
        // エンティティに名前付きタグを適用
        val newName = if (isCreativeMode) {
            "${ChatColor.GOLD}${ChatColor.stripColor(entity.customName)}"
        } else {
            "${ChatColor.WHITE}${ChatColor.stripColor(entity.customName)}"
        }
        entity.customName = newName
        entity.setAI(!isCreativeMode)
        Bukkit.getLogger().info("*** reload ${entity.customName}")
    }

    override fun release() {
        _taskRunner.execute({
            _entityRepository.findById(uuid)?.let {
                _entityRepository.delete(it)
            }
            entity.setAI(true)
            _entityUtil.free()
            entity.isCustomNameVisible = false
            entity.customName = null
            Bukkit.getLogger().info("*** release ${entity.customName}")
        }, 0)
    }

    override fun comeHere(location: Location, target: Location, direction: Vector) {
        _taskRunner.execute({
            val fixLocation =
                Location(
                    location.world,
                    location.blockX + 0.5,
                    location.y,
                    location.blockZ + 0.5,
                    location.yaw,
                    location.pitch
                )

            val origin = fixLocation.clone().setDirection(direction)

            val yaw = origin.yaw
            val roundedYaw = (yaw / 90).roundToInt() * 90 // 90で割り切れる値に修正

            val newLocation =
                Location(location.world, fixLocation.x, fixLocation.y, fixLocation.z, roundedYaw.toFloat(), 0f)
            updateLocation(newLocation)
            clearArea()
        }, period)
    }

    override fun hasControl(player: Player): Boolean {
        return player.isOp || _entityRepository.findById(entity.uniqueId)?.ownerUuid == player.uniqueId
    }

    @HostAccess.Export
    override fun reset() {
        if (_saveArea != null) {
            _taskRunner.execute({
                //もとに戻す
                _saveArea!!.restore()
                updateLocation(_saveArea!!.startedLocation)
                _saveArea = null
            }, period)
        }
    }

    @HostAccess.Export
    override fun initArea() {
        _taskRunner.execute({
            _saveArea = SaveArea(entity.location.clone(), inventory)
        }, 0)
    }

    override fun clearArea() {
        _taskRunner.execute({
            _saveArea = null
        }, 0)
    }

    override fun hasSavedArea(): Boolean {
        return _saveArea != null
    }

    override val period: Long
        get() = 7L

    override val selectedItem: ItemStack?
        get() = _inventory.selectItem(_selectedSlotNo)

    override fun onEntityDeath(event: EventData) {
        for (i in 0 until _inventory.size) {
            val itemStack = _inventory.getItem(i) ?: continue
            val item = entity.world.dropItemNaturally(entity.location, itemStack)
            item.velocity = item.velocity.multiply(0.1)
        }
        _inventory.clear()
    }

    override fun getInventory(): Inventory {
        return _inventory.instance
    }

    override fun isAI(): Boolean {
        return entity.hasAI()
    }

    override fun stay() {
        _taskRunner.execute({
            _entityUtil.stay()
        }, period)
    }

    override fun free() {
        _taskRunner.execute({
            _entityUtil.free()
        }, period)
    }

    /**
     * エンティティの位置を取得します
     * @return 位置情報（x, y, z）を含むマップ
     */
    @HostAccess.Export
    fun getPosition(): Any? {
        return toProxyObject(getPositionMap())
    }

    override fun getPositionMap(): Map<String, Int> {
        val location = BlockUtil.getFitLocation(entity.location)
        return mapOf(
            "x" to location.blockX,
            "y" to location.blockY,
            "z" to location.blockZ,
        )
    }

    /**
     * エンティティの向きを取得します
     * @return 向き情報（x, y, z）を含むマップ
     */
    @HostAccess.Export
    fun getDirection(): Any? {
        return toProxyObject(getDirectionMap())
    }

    override fun getDirectionMap(): Map<String, Double> {
        return mapOf(
            "x" to entity.location.direction.x,
            "y" to entity.location.direction.y,
            "z" to entity.location.direction.z,
        )
    }

    /**
     * エンティティの高さを取得します
     * @return エンティティの高さ
     */
    @HostAccess.Export
    override fun getHeight(): Double {
        return entity.height
    }

    /**
     * エンティティの幅を取得します
     * @return エンティティの幅
     */
    @HostAccess.Export
    override fun getWidth(): Double {
        return entity.width
    }

    /**
     * エンティティの体力を取得します
     * @return エンティティの体力値
     */
    @HostAccess.Export
    override fun getHealth(): Double {
        return entity.health
    }

    /**
     * エンティティの名前を取得します
     * @return エンティティの名前
     */
    @HostAccess.Export
    override fun getName(): String {
        return ChatColor.stripColor(entity.customName) ?: ""
    }

    /**
     * エンティティの種類を取得します
     * @return エンティティの種類
     */
    @HostAccess.Export
    override fun getType(): String {
        return entity.type.name
    }

    /**
     * エンティティのUUIDを取得します
     * @return エンティティのUUID
     */
    @HostAccess.Export
    override fun uniqueId(): String {
        return entity.uniqueId.toString()
    }

    /**
     * エンティティの所有者のUUIDを取得します
     * @return 所有者のUUID
     */
    @HostAccess.Export
    override fun getOwner(): String {
        return _entityRepository.findById(entity.uniqueId)?.ownerUuid?.toString() ?: ""
    }


    override fun isRunning(): Boolean {
        return scriptExecutor?.isRunning ?: false
    }

    override fun isBinding(): Boolean {
        return scriptExecutor != null
    }

    override fun onUpdate() {
        if (this.scriptExecutor != null) {
            //Bukkit.getLogger().info("onUpdate 直接実行")
            Bukkit.getScheduler().runTaskAsynchronously(HackCraft2.instance, Runnable {
                _scriptService.execute(scriptExecutor!!, EventData("onUpdate", null))
            })
        }
    }

    override fun onStart() {
        if (this.scriptExecutor != null) {
            Bukkit.getLogger().info("onStart 直接実行")
            Bukkit.getScheduler().runTaskAsynchronously(HackCraft2.instance, Runnable {
                _scriptService.execute(scriptExecutor!!, EventData("onStart", null))
            })
        }
    }

    override fun onStop() {
        if (this.scriptExecutor != null) {
            Bukkit.getLogger().info("onStop 直接実行")
            Bukkit.getScheduler().runTaskAsynchronously(HackCraft2.instance, Runnable {
                _scriptService.execute(scriptExecutor!!, EventData("onStop", null))
            })
        }
    }

    override fun bind(executor: ScriptExecutor) {
        clearListeners()
        _eventHandlers.clear()
        scriptExecutor = executor
    }

    override fun interrupt() {
        if (this.scriptExecutor != null) {
            clearListeners()
            _eventHandlers.clear()
            Bukkit.getScheduler().runTaskAsynchronously(HackCraft2.instance, Runnable {
                scriptExecutor!!.stop()
                scriptExecutor = null
            })

        }
    }

    ///

    @HostAccess.Export
    override fun teleport(x: Double, y: Double, z: Double, cord: String): Boolean {
        return _taskRunner.executeWithResult({
            val newLocation = _entityUtil.newLocation(
                x,
                y,
                z,
                cord
            )
            var fixLocation =
                Location(
                    newLocation.world,
                    newLocation.blockX + 0.5,
                    newLocation.y,
                    newLocation.blockZ + 0.5,
                    newLocation.yaw,
                    newLocation.pitch
                )
            if (BlockUtil.canEntityMoveToLocation(fixLocation)) {
                _entityUtil.setGravity(fixLocation)
                updateLocation(fixLocation)
                return@executeWithResult true
            } else {
                playFailSound()
                _entityUtil.displayChatBubble(Messages.MOVE_ERROR);
                return@executeWithResult false
            }
        }, period).get()
    }

    @HostAccess.Export
    override fun addForce(fx: Double, fy: Double, fz: Double): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            Bukkit.getLogger().info("******** addForce")
            if (isCreativeMode) {
                playFailSound()
                _entityUtil.displayChatBubble(Messages.VECTOR_ERROR);
                return@executeWithResult false
            }
            if (!_entityUtil.canAddForce()) {
                return@executeWithResult false
            }

            if (!_entityUtil.isEntityStationary()) {
                return@executeWithResult false
            }

            val xz = Vector(fx, 0.0, fz)
            val inputSpeed = xz.length()
            val speed = inputSpeed.coerceIn(0.0, 1.5)
            val yaw = Math.toRadians(entity.location.yaw.toDouble())
            val x = -kotlin.math.sin(yaw)
            val z = kotlin.math.cos(yaw)
            val direction = Vector(x, 0.0, z).normalize().multiply(speed).setY(fy.coerceIn(0.0, 1.0))

            entity.velocity = direction
            Bukkit.getLogger().info("*********** addForce ${direction.x} ${direction.y} ${direction.z}")
            return@executeWithResult true
        }, 0).get()
    }

    @HostAccess.Export
    override fun forward(n: Int = 1): Boolean {
        return moveForward(n)
    }

    @HostAccess.Export
    override fun back(n: Int = 1): Boolean {
        return moveForward(-n)
    }

    @HostAccess.Export
    override fun up(n: Int = 1): Boolean {
        return upDown(n)
    }

    @HostAccess.Export
    override fun down(n: Int = 1): Boolean {
        return upDown(-n)
    }

    @HostAccess.Export
    override fun stepLeft(n: Int): Boolean {
        return moveEntityHorizontal(-n)
    }

    @HostAccess.Export
    override fun stepRight(n: Int): Boolean {
        return moveEntityHorizontal(n)
    }

    @HostAccess.Export
    override fun turn(degrees: Double): Boolean {
        Bukkit.getLogger().info("in turn $degrees ${entity.location.yaw}, ${entity.location.pitch}")
        return _taskRunner.executeWithResult({
            val newLocation = entity.location
            val currentYaw = newLocation.yaw
            val newYaw = (currentYaw + degrees).toFloat()
            newLocation.yaw = newYaw
            Bukkit.getLogger().info("in turn new $degrees ${newLocation.yaw}, ${newLocation.pitch}")
            updateRotation(newYaw, 0.0f) //newLocation.pitch)
            true
        }, period).get()
    }

    @HostAccess.Export
    override fun facing(degrees: Double) {
        _taskRunner.execute({
            val newLocation = entity.location.clone()
            newLocation.yaw = degrees.toFloat()
            updateRotation(degrees.toFloat(), 0.0f)//newLocation.pitch)
        }, period)
    }

    @HostAccess.Export
    override fun turnLeft(): Boolean {
        return turn(-90.0)
    }

    @HostAccess.Export
    override fun turnRight(): Boolean {
        return turn(90.0)
    }


    @HostAccess.Export
    override fun moveOwner(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            _entityRepository.findById(uuid)?.let { namedEntity ->
                Bukkit.getPlayer(namedEntity.ownerUuid)?.let {
                    updateLocation(it.location)
                    true // プレイヤーが存在する場合はtrueを返す
                } ?: false // プレイヤーが存在しない場合はfalseを返す
            } ?: false // エンティティが存在しない場合はfalseを返す
        }, period).get()
    }


    @HostAccess.Export
    override fun selectItem(slotNo: Int): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            if (slotNo < 0 || slotNo >= _inventory.size) {
                _entityUtil.displayChatBubble(Messages.GRAB_ERROR);
                return@executeWithResult false
            }
            this._selectedSlotNo = slotNo
            _inventory.selectItem(_selectedSlotNo)
            return@executeWithResult true
        }, 0).get()
    }

    @HostAccess.Export
    fun findNearbyBlockX(x: Double, y: Double, z: Double, cord: String, block: String, maxDepth: Int): Any? {
        val ret = findNearbyBlockXMap(x, y, z, cord, block, maxDepth)
        if (ret != null) {
            return toProxyObject(ret)
        } else {
            return null
        }
    }

    override fun findNearbyBlockXMap(
        x: Double,
        y: Double,
        z: Double,
        cord: String,
        block: String,
        maxDepth: Int
    ): Map<String, Any?>? {
        val visited = mutableSetOf<Block>()  // 訪れたブロックを追跡するためのセット
        val location = _entityUtil.newLocation(x, y, z, cord)
        val find = findWaterSourceRecursive(location.block, block, visited, maxDepth)
        if (find != null) {
            return BlockUtil.toMapData(find.location)
        } else {
            return null
        }
    }

    private fun findWaterSourceRecursive(
        block: Block,
        searchBlock: String,
        visited: MutableSet<Block>,
        remainingDepth: Int
    ): Block? {
        // 再帰が深すぎた場合は終了
        if (remainingDepth <= 0) return null

        // ブロックが訪れたことがある場合はスキップ
        if (visited.contains(block)) return null

        // ブロックが水であり、水源ブロックであればそれを返す
        if (BlockUtil.isSameBlock(searchBlock, block)) return block

        // 訪れたブロックとして記録
        visited.add(block)

        // ブロックの隣接する6方向を再帰的に探索
        val adjacentBlocks = listOf(
            block.getRelative(1, 0, 0),  // 東
            block.getRelative(-1, 0, 0), // 西
            block.getRelative(0, 1, 0),  // 上
            block.getRelative(0, -1, 0), // 下
            block.getRelative(0, 0, 1),  // 南
            block.getRelative(0, 0, -1)  // 北
        )

        for (adjacent in adjacentBlocks) {
            val waterSource = findWaterSourceRecursive(adjacent, searchBlock, visited, remainingDepth - 1)
            if (waterSource != null) {
                return waterSource
            }
        }

        return null
    }

    @HostAccess.Export
    fun getInventoryItem(x: Int, y: Int, z: Int, slot: Int): Any? {
        return toProxyObject(getInventoryItemMap(x, y, z, slot))
    }

    override fun getInventoryItemMap(x: Int, y: Int, z: Int, slot: Int): Map<String, Any?> {
        return _taskRunner.executeWithResult<Map<String, Any?>>({
            val location = Location(entity.world, x.toDouble(), y.toDouble(), z.toDouble())
            val block = location.block
            val state = block.state
            //指定されたブロックがInventoryなら処理をする
            if (state is InventoryHolder) {
                val itemStack = state.inventory.getItem(slot) ?: return@executeWithResult mapOf(
                    "slot" to slot,
                    "name" to "air",
                    "amount" to 0
                )
                return@executeWithResult mapOf<String, Any?>(
                    "slot" to slot,
                    "name" to itemStack.type.name.lowercase(),
                    "amount" to itemStack.amount
                )
            } else {
                return@executeWithResult mapOf(
                    "slot" to slot,
                    "name" to "air",
                    "amount" to 0
                )
            }
        }, 0).get()
    }

    @HostAccess.Export
    override fun swapInventoryItem(x: Int, y: Int, z: Int, slot1: Int, slot2: Int): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val location = Location(entity.world, x.toDouble(), y.toDouble(), z.toDouble())
            val block = location.block
            val state = block.state
            //指定されたブロックがInventoryなら処理をする
            if (state is InventoryHolder) {
                if (state.inventory.size <= slot1) return@executeWithResult false
                if (state.inventory.size <= slot2) return@executeWithResult false

                val item1 = state.inventory.getItem(slot1)
                val item2 = state.inventory.getItem(slot2)

                state.inventory.setItem(slot1, item2)
                state.inventory.setItem(slot2, item1)
                return@executeWithResult true
            } else {
                return@executeWithResult false
            }
        }, 0).get()
    }

    @HostAccess.Export
    override fun moveInventoryItem(x: Int, y: Int, z: Int, slot1: Int, slot2: Int): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val location = Location(entity.world, x.toDouble(), y.toDouble(), z.toDouble())
            val block = location.block
            val state = block.state
            //指定されたブロックがInventoryなら処理をする
            if (state is InventoryHolder) {

                val item1 = state.inventory.getItem(slot1) ?: return@executeWithResult false
                val item2 = state.inventory.getItem(slot2)

                if (item2 == null) {
                    // 移動先が空の場合、そのまま移動
                    state.inventory.setItem(slot2, item1)
                    state.inventory.clear(slot1)
                } else if (item1.isSimilar(item2)) {
                    // 同じアイテムの場合、アイテムを合算
                    val totalAmount = item1.amount + item2.amount
                    val maxAmount = item1.maxStackSize
                    if (totalAmount <= maxAmount) {
                        item2.amount = totalAmount
                        state.inventory.clear(slot1)
                    } else {
                        // スタックが64を超える場合、アイテム2を64に設定
                        item2.amount = maxAmount
                        // アイテム1の量を超過分だけ減らす
                        item1.amount = totalAmount - maxAmount
                        state.inventory.setItem(slot1, item1)
                    }
                } else {
                    return@executeWithResult false
                }

                return@executeWithResult true
            } else {
                return@executeWithResult false
            }
        }, 0).get()
    }

    @HostAccess.Export
    override fun storeInventoryItem(x: Int, y: Int, z: Int, slot1: Int, slot2: Int): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val location = Location(entity.world, x.toDouble(), y.toDouble(), z.toDouble())
            val block = location.block
            val state = block.state
            //指定されたブロックがInventoryなら処理をする
            if (state is InventoryHolder) {

                val item1 = _inventory.getItem(slot1) ?: return@executeWithResult false
                val item2 = state.inventory.getItem(slot2)

                if (item2 == null) {
                    // 移動先が空の場合、そのまま移動
                    state.inventory.setItem(slot2, item1)
                    _inventory.clear(slot1)
                } else if (item1.isSimilar(item2)) {
                    // 同じアイテムの場合、アイテムを合算
                    val totalAmount = item1.amount + item2.amount
                    val maxAmount = item1.maxStackSize
                    if (totalAmount <= maxAmount) {
                        item2.amount = totalAmount
                        _inventory.clear(slot1)
                    } else {
                        // スタックが64を超える場合、アイテム2を64に設定
                        item2.amount = maxAmount
                        // アイテム1の量を超過分だけ減らす
                        item1.amount = totalAmount - maxAmount
                        _inventory.setItem(slot1, item1)
                    }
                } else {
                    return@executeWithResult false
                }

                return@executeWithResult true
            } else {
                return@executeWithResult false
            }
        }, 0).get()
    }

    @HostAccess.Export
    override fun retrieveInventoryItem(x: Int, y: Int, z: Int, slot1: Int, slot2: Int): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val location = Location(entity.world, x.toDouble(), y.toDouble(), z.toDouble())
            val block = location.block
            val state = block.state
            //指定されたブロックがInventoryなら処理をする
            if (state is InventoryHolder) {

                val item1 = state.inventory.getItem(slot1) ?: return@executeWithResult false
                val item2 = _inventory.getItem(slot2)

                if (item2 == null) {
                    // 移動先が空の場合、そのまま移動
                    _inventory.setItem(slot2, item1)
                    state.inventory.clear(slot1)
                } else if (item1.isSimilar(item2)) {
                    // 同じアイテムの場合、アイテムを合算
                    val totalAmount = item1.amount + item2.amount
                    val maxAmount = item1.maxStackSize
                    if (totalAmount <= maxAmount) {
                        item2.amount = totalAmount
                        state.inventory.clear(slot1)
                    } else {
                        // スタックが64を超える場合、アイテム2を64に設定
                        item2.amount = maxAmount
                        // アイテム1の量を超過分だけ減らす
                        item1.amount = totalAmount - maxAmount
                        state.inventory.setItem(slot1, item1)
                    }
                } else {
                    return@executeWithResult false
                }

                return@executeWithResult true
            } else {
                return@executeWithResult false
            }
        }, 0).get()
    }

    @HostAccess.Export
    override fun setItem(slotNo: Int, item: String): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            if (slotNo < 0 || slotNo >= _inventory.size) {
                _entityUtil.displayChatBubble(Messages.SET_ITEM_ERROR);
                return@executeWithResult false
            }

            if (!isCreativeMode) {
                _entityUtil.displayChatBubble(Messages.CRIATIVE_ERROR);
                return@executeWithResult false
            }

            val itemName = if (item.startsWith("minecraft:")) item.substring(10) else item
            val split = itemName.split(":") // "blockName:data" 形式を想定
            val blockName = split[0]
            val data = if (split.size > 1) split[1].toIntOrNull() ?: 0 else 0 // dataが存在しない場合は0とする

            val material = Material.getMaterial(blockName.uppercase())
            if (material == null) {
                Bukkit.getLogger().info("Material not found: $item")
                _entityUtil.displayChatBubble(Messages.SET_ITEM_ERROR);
                return@executeWithResult false
            }
            val itemStack = ItemStack(material)
            if (data > 0) {
                try {
                    val metaData = itemStack.itemMeta as? BlockStateMeta
                    if (metaData != null) {
                        val blockState = metaData.blockState
                        blockState.data.data = data.toByte()
                        metaData.blockState = blockState
                        itemStack.itemMeta = metaData
                    }
                } catch (e: Exception) {
                    Bukkit.getLogger().info("Failed to set block data: $e")
                }
            }
            _inventory.setItem(slotNo, itemStack)
            return@executeWithResult true
        }, 0).get()
    }

    @HostAccess.Export
    override fun getItemCountInSlot(slot: Int): Int {
        return _taskRunner.executeWithResult({
            val itemStack = _inventory.getItem(slot) ?: return@executeWithResult 0
            return@executeWithResult itemStack.amount
        }, 0).get()
    }

    @HostAccess.Export
    fun getItem(slot: Int): Any? {
        return toProxyObject(getItemMap(slot))
    }

    override fun getItemMap(slot: Int): Map<String, Any?> {
        return _taskRunner.executeWithResult({
            val itemStack = _inventory.getItem(slot) ?: return@executeWithResult mapOf(
                "slot" to slot,
                "name" to "air",
                "amount" to 0
            )
            return@executeWithResult mapOf<String, Any?>(
                "slot" to slot,
                "name" to itemStack.type.name.lowercase(),
                "amount" to itemStack.amount
            )
        }, 0).get()
    }

    @HostAccess.Export
    override fun actionX(x: Int, y: Int, z: Int, cord: String): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val location = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
            val actionBlock = location.block
            val blockState: BlockState = actionBlock.location.block.state
            val blockData: BlockData = blockState.blockData
            val blocks = BlockUtil.getRelatedBlocks(actionBlock)
            //backup
            _saveArea?.addChangeBlocks(blocks)

            if (blockData is Openable) { //ドアの制御
                blockData.isOpen = !blockData.isOpen
                actionBlock.setBlockData(blockData, true)
                val blockChangedEvent = BlockChangedEvent(location, blocks)
                server.pluginManager.callEvent(blockChangedEvent)
                return@executeWithResult true
            } else if (blockData is Powerable) { //スイッチの制御
                val isPowered = !blockData.isPowered
                blockData.isPowered = isPowered
                actionBlock.setBlockData(blockData, true)
                BlockUtil.playBlockPowerableSound(entity, actionBlock, isPowered)
                if (BlockUtil.isAutoPowerdownBlock(actionBlock)) {
                    Bukkit.getScheduler().runTaskLater(HackCraft2.instance, Runnable {
                        blockData.isPowered = false
                        actionBlock.setBlockData(blockData, true)
                        BlockUtil.playBlockPowerableSound(entity, actionBlock, false)
                    }, 15)
                }
                val blockChangedEvent = BlockChangedEvent(location, blocks)
                server.pluginManager.callEvent(blockChangedEvent)
                return@executeWithResult true
            } else {
                _entityUtil.displayChatBubble(Messages.ACTION_ERROR);

                return@executeWithResult false
            }
        }, period).get()
    }

    @HostAccess.Export
    fun action(): Boolean {
        return actionX(0, 0, 1, "^")
    }

    @HostAccess.Export
    fun actionDown(): Boolean {
        return actionX(0, -1, 0, "^")
    }

    @HostAccess.Export
    fun actionUp(): Boolean {
        return actionX(0, 1, 0, "^")
    }


    @HostAccess.Export
    override fun placeX(x: Int, y: Int, z: Int, cord: String, side: String): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val selectItemStack = _inventory.selectItem(_selectedSlotNo) ?: ItemStack(Material.AIR)
            if (!canBuild(entity.location)) {
                playFailSound()
                _entityUtil.displayChatBubble(Messages.PLACE_ANTI_BUILD_ERROR);
                return@executeWithResult false
            }

            //アイテムが選択されていない場合はブロックを壊す
            if (selectItemStack.type == Material.AIR) {
                val newLocation = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
                val ret = newLocation.block
                if (ret.type == Material.AIR) {
                    return@executeWithResult true
                }
                if (!BlockUtil.canBreakableBlock(ret)) {
                    _entityUtil.displayChatBubble(Messages.DIG_ERROR);
                    return@executeWithResult false
                }

                // ブロックを壊す
                breakBlock(ret)
                return@executeWithResult true
            }

            //アイテムが選択されている場合はブロックを置く
            val itemStack = selectItemStack.clone()
            itemStack.amount = 1

            val location = _entityUtil.newLocation(
                x.toDouble(),
                y.toDouble(),
                z.toDouble(),
                cord
            )

            //ブロックがおける場所かどうかチェック
            if (itemStack.type.isBlock && BlockUtil.canPlaceBlock(location)) {

                val isAttachableBlock = ItemStackUtil.isAttachableBlock(itemStack)
                if (!isAttachableBlock) {
                    // 置きたいブロックの種類
                    entity.swingMainHand()
                    placeBlock(location, itemStack, side)
                    return@executeWithResult true
                }
            } else if (itemStack.type.isBlock && ItemStackUtil.isAttachableBlock(itemStack)) {
                //ボタンとかレバーなど
                val targetBlock = location.block
                val blockFace = _entityUtil.getClosestBlockFace(targetBlock.location, entity.location).oppositeFace

                if (blockFace == BlockFace.UP) {
                    return@executeWithResult false
                } else if (blockFace == BlockFace.DOWN) {
                    val blockData = itemStack.type.createBlockData()
                    val placeLocation = targetBlock.getRelative(blockFace.oppositeFace)
                    if (blockData is FaceAttachable) {
                        blockData.attachedFace = FaceAttachable.AttachedFace.FLOOR
                        stateBlockChange(placeLocation, blockData)
                    } else {
                        stateBlockChange(placeLocation, blockData)
                    }
                    if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)

                    return@executeWithResult true
                } else if (!targetBlock.isPassable) {
                    val compMaterial = ItemStackUtil.getWallCompatibleMaterial(itemStack, blockFace)
                    val blockData = compMaterial.createBlockData()
                    if (blockData is Directional) {
                        blockData.facing = blockFace
                        val placeLocation = targetBlock.getRelative(blockFace)
                        stateBlockChange(placeLocation, blockData)
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)

                        return@executeWithResult true
                    } else if (blockData is FaceAttachable) {
                        blockData.attachedFace = FaceAttachable.AttachedFace.WALL
                        stateBlockChange(targetBlock, blockData)
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)

                        return@executeWithResult true
                    }
                    return@executeWithResult false
                } else if (!targetBlock.getRelative(BlockFace.DOWN).isPassable) {
                    val blockData = itemStack.type.createBlockData()
                    if (blockData is FaceAttachable) {
                        blockData.attachedFace = FaceAttachable.AttachedFace.FLOOR
                        stateBlockChange(targetBlock, blockData)
                    } else {
                        stateBlockChange(targetBlock, itemStack.type.createBlockData())
                    }
                    if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                    return@executeWithResult true
                }
            }

            playFailSound()
            _entityUtil.displayChatBubble(Messages.PLACE_ERROR);
            return@executeWithResult false
        }, period).get()
    }

    @HostAccess.Export
    fun placeX(x: Int, y: Int, z: Int, cord: String): Boolean {
        return placeX(x, y, z, cord, "")
    }

    @HostAccess.Export
    fun placeX(x: Int, y: Int, z: Int): Boolean {
        return placeX(x, y, z, "^", "")
    }

    @HostAccess.Export
    override fun place(side: String): Boolean {
        return placeX(0, 0, 1, "^", side)
    }

    @HostAccess.Export
    fun place(): Boolean {
        return place("")
    }

    @HostAccess.Export
    override fun placeDown(side: String): Boolean {
        return placeX(0, -1, 0, "^", side)
    }

    @HostAccess.Export
    fun placeDown(): Boolean {
        return placeDown("")
    }

    @HostAccess.Export
    override fun placeUp(side: String): Boolean {
        return placeX(0, 1, 0, "^", side)
    }

    @HostAccess.Export
    fun placeUp(): Boolean {
        return placeUp("")
    }


    @HostAccess.Export
    override fun setBlock(x: Int, y: Int, z: Int, cord: String, block: String, side: String): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            return@executeWithResult _setBlock(x, y, z, cord, block, side)
        }, 0).get()
    }

    @HostAccess.Export
    fun setBlock(x: Int, y: Int, z: Int, cord: String, block: String): Boolean {
        return setBlock(x, y, z, cord, block, "")
    }

    @HostAccess.Export
    override fun setBlocks(
        blocks: List<Map<String, Any>>
    ): Boolean {
        // MapをBlockDataに変換
        val list = blocks.mapNotNull { mapToBlockData(it) }
        if (list.isEmpty()) return false // データ変換失敗時は終了
        return _taskRunner.executeWithResult<Boolean>({
            for (block in list) {
                val ret = _setBlock(block.x, block.y, block.z, block.cord, block.block, block.side)
                if (!ret) return@executeWithResult false
            }
            return@executeWithResult true
        }, 0).get()
    }

    @HostAccess.Export
    override fun fillBlock(
        x1: Int,
        y1: Int,
        z1: Int,
        x2: Int,
        y2: Int,
        z2: Int,
        cord: String,
        block: String,
        side: String
    ): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            // 指定された範囲内のすべての座標をループする
            for (x in minOf(x1, x2)..maxOf(x1, x2)) {
                for (y in minOf(y1, y2)..maxOf(y1, y2)) {
                    for (z in minOf(z1, z2)..maxOf(z1, z2)) {
                        // 各座標にブロックを置く
                        val success = setBlock(x, y, z, cord, block, side)
                        if (!success) {
                            return@executeWithResult false // ブロックの設置に失敗した場合、処理を中断
                        }
                    }
                }
            }
            return@executeWithResult true
        }, 0).get()
    }

    @HostAccess.Export
    fun fillBlock(
        x1: Int,
        y1: Int,
        z1: Int,
        x2: Int,
        y2: Int,
        z2: Int,
        cord: String,
        block: String,
    ): Boolean {
        return fillBlock(x1, y1, z1, x2, y2, z2, cord, block, "")
    }

    @HostAccess.Export
    override fun useItemX(x: Int, y: Int, z: Int, cord: String): Boolean {
        Bukkit.getLogger().info("useItemX($y, $z) by $_selectedSlotNo")
        return _taskRunner.executeWithResult<Boolean>({
            val itemStack = _inventory.selectItem(_selectedSlotNo) ?: return@executeWithResult false
            if (z < 0) {
                playFailSound()
                _entityUtil.displayChatBubble(Messages.USE_ERROR);
                return@executeWithResult false
            }
            val location = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
            val targetBlock = location.block
            try {
                if (ItemStackUtil.isCart(itemStack.type)) {
                    if (ItemStackUtil.isRail(location.block.type)) {
                        val entityTyp = ItemStackUtil.getEntityTypeFromItemStack(itemStack)
                        if (entityTyp != null) {
                            val cart = location.world!!.spawnEntity(location, entityTyp)
                            if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                            val sound = SoundMapping.getPlaceSound(itemStack.type)
                            entity.world.playSound(location, sound, 1f, 1f)
                            return@executeWithResult true
                        }
                    }
                } else if (ItemStackUtil.isBoat(itemStack.type)) {
                    val entityTyp = ItemStackUtil.getEntityTypeFromItemStack(itemStack)
                    if (entityTyp != null) {
                        val cart = location.world!!.spawnEntity(location, entityTyp)
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                        val sound = SoundMapping.getPlaceSound(itemStack.type)
                        entity.world.playSound(location, sound, 1f, 1f)
                        return@executeWithResult true
                    }
                } else if (ItemStackUtil.isBucket(itemStack)) {
                    if (itemStack.type == Material.BUCKET) {
                        // 空のバケツを持っている場合の処理
                        val ret = BlockUtil.pickupBucket(targetBlock)
                        entity.swingMainHand()
                        if (ret != null) {
                            if (!isCreativeMode) _inventory.removeItem(ItemStack(itemStack.type, 1))
                            _inventory.addItem(ret)
                        }
                        return@executeWithResult true
                    } else {
                        // 何かがはいっているバケツを持っている場合の処理
                        if (BlockUtil.spillBucket(targetBlock, itemStack)) {
                            entity.swingMainHand()
                            if (!isCreativeMode) {
                                _inventory.setItem(_selectedSlotNo, ItemStack(Material.BUCKET))
                            }
                            return@executeWithResult true
                        }
                        return@executeWithResult false
                    }
                } else if (ItemStackUtil.isHoe(itemStack)) {
                    //指定されたブロックを耕すことができればたがやす
                    if (BlockUtil.plowField(targetBlock)) {
                        entity.swingMainHand()
                        return@executeWithResult true
                    }
                } else if (ItemStackUtil.isShovel(itemStack)) {
                    //指定されたブロックを平らにすることができたら平らにする
                    if (BlockUtil.flattenGround(targetBlock)) {
                        entity.swingMainHand()
                        val blockChangedEvent = BlockChangedEvent(targetBlock.location, listOf(targetBlock))
                        server.pluginManager.callEvent(blockChangedEvent)
                        return@executeWithResult true
                    }
                } else if (ItemStackUtil.isAxe(itemStack)) {
                    if (BlockUtil.stripLog(targetBlock)) {
                        entity.swingMainHand()
                        val blockChangedEvent = BlockChangedEvent(targetBlock.location, listOf(targetBlock))
                        server.pluginManager.callEvent(blockChangedEvent)
                        return@executeWithResult true
                    } else if (BlockUtil.changeCopper(targetBlock)) {
                        entity.swingMainHand()
                        val blockChangedEvent = BlockChangedEvent(targetBlock.location, listOf(targetBlock))
                        server.pluginManager.callEvent(blockChangedEvent)
                        return@executeWithResult true
                    }
                } else if (ItemStackUtil.isCrop(itemStack)) {
                    //足元のブロックを取得
                    if (GardeningUtil.sowing(targetBlock, itemStack)) {
                        entity.swingMainHand()
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                        return@executeWithResult true
                    }
                } else if (ItemStackUtil.isPlantable(itemStack)) {
                    //足元のブロックを取得
                    if (GardeningUtil.planting(targetBlock, itemStack)) {
                        entity.swingMainHand()
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                        return@executeWithResult true
                    }
                } else if (ItemStackUtil.isSapling(itemStack)) {
                    //足元のブロックを取得
                    if (ForestryUtil.plant(targetBlock, itemStack)) {
                        entity.swingMainHand()
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                        return@executeWithResult true
                    }
                } else if (itemStack.type == Material.SHEARS) {
                    //ハサミ
                    if (GardeningUtil.shearsSheep(targetBlock)) {
                        entity.swingMainHand()
                        val items = entity.getNearbyEntities(2.0, 2.0, 2.0)
                            .filterIsInstance<Item>()
                            .filter { it.itemStack.type != Material.AIR }
                        if (items.isNotEmpty()) {
                            for (item in items) {
                                _inventory.addItem(item.itemStack)
                                item.remove()
                            }
                        }
                        return@executeWithResult true
                    } else
                    // ハサミで蜘蛛の糸を回収する処理
                        if (GardeningUtil.shearsSpiderWeb(targetBlock)) {
                            entity.swingMainHand()
                            val items = entity.getNearbyEntities(2.0, 2.0, 2.0)
                                .filterIsInstance<Item>()
                                .filter { it.itemStack.type != Material.AIR }
                            if (items.isNotEmpty()) {
                                for (item in items) {
                                    _inventory.addItem(item.itemStack)
                                    item.remove()
                                }
                            }
                            return@executeWithResult true
                        }
                } else if (itemStack.type == Material.BONE_MEAL) {
                    //骨粉
                    if (GardeningUtil.grow(targetBlock)) {
                        entity.swingMainHand()
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                        return@executeWithResult true
                    }
                } else if (itemStack.type == Material.FLINT_AND_STEEL) {
                    //火打石
                    if (ForestryUtil.makeFire(targetBlock)) {
                        entity.swingMainHand()
                        return@executeWithResult true
                    }
                } else if (itemStack.type == Material.SPLASH_POTION) {
                    _entityUtil.throwSplashPotion(x, y, z, itemStack)
                    if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                    return@executeWithResult true
                } else if (itemStack.type == Material.SNOWBALL) {
                    _entityUtil.throwSnowball(entity, x, y, z)
                    if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                    return@executeWithResult true
                } else if (itemStack.type == Material.ENDER_PEARL) {
                    _entityUtil.throwEnderPearl(entity, x, y, z)
                    if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                    return@executeWithResult true
                } else if (itemStack.type.isBlock && ItemStackUtil.isAttachableBlock(itemStack) && canBuild(
                        entity.location
                    )
                ) {
                    //指定されたブロックを耕すことができればたがやす
                    val blockFace = _entityUtil.getClosestBlockFace(targetBlock.location, entity.location).oppositeFace

                    if (blockFace == BlockFace.UP) {
                        return@executeWithResult false
                    } else if (blockFace == BlockFace.DOWN) {
                        val blockData = itemStack.type.createBlockData()
                        val placeLocation = targetBlock.getRelative(blockFace.oppositeFace)
                        if (blockData is FaceAttachable) {
                            blockData.attachedFace = FaceAttachable.AttachedFace.FLOOR
                            stateBlockChange(placeLocation, blockData)
                        } else {
                            stateBlockChange(placeLocation, blockData)
                        }
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)

                        return@executeWithResult true
                    } else if (!targetBlock.isPassable) {
                        val compMaterial = ItemStackUtil.getWallCompatibleMaterial(itemStack, blockFace)
                        val blockData = compMaterial.createBlockData()
                        if (blockData is Directional) {
                            blockData.facing = blockFace
                            val placeLocation = targetBlock.getRelative(blockFace)
                            stateBlockChange(placeLocation, blockData)
                            if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)
                            return@executeWithResult true
                        } else if (blockData is FaceAttachable) {
                            blockData.attachedFace = FaceAttachable.AttachedFace.WALL
                            stateBlockChange(targetBlock, blockData)
                            if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)

                            return@executeWithResult true
                        }
                    } else if (!targetBlock.getRelative(BlockFace.DOWN).isPassable) {
                        val blockData = itemStack.type.createBlockData()
                        if (blockData is FaceAttachable) {
                            blockData.attachedFace = FaceAttachable.AttachedFace.FLOOR
                            stateBlockChange(targetBlock, blockData)
                        } else {
                            stateBlockChange(targetBlock, itemStack.type.createBlockData())
                        }
                        if (!isCreativeMode) _inventory.removeItem(_selectedSlotNo, itemStack, 1)

                        return@executeWithResult true
                    }
                }

                playFailSound()
                _entityUtil.displayChatBubble(Messages.USE_ERROR);
                return@executeWithResult false
            } finally {
                val blockChangedEvent = BlockChangedEvent(targetBlock.location, listOf(targetBlock))
                server.pluginManager.callEvent(blockChangedEvent)
            }
        }, period).get()
    }

    @HostAccess.Export
    fun useItemX(x: Int, y: Int, z: Int): Boolean {
        return useItemX(x, y, z, "^")
    }

    @HostAccess.Export
    fun useItem(): Boolean {
        return useItemX(0, 0, 1, "^")
    }

    @HostAccess.Export
    fun useItemDown(): Boolean {
        return useItemX(0, -1, 0, "^")
    }

    @HostAccess.Export
    fun useItemUp(): Boolean {
        return useItemX(0, 1, 0, "^")
    }

    private fun smartDig(x: Int, y: Int, z: Int, cord: String): String? {
        val newLocation = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
        val ret = newLocation.block
        if (!BlockUtil.canBreakableBlock(ret)) {
            return Messages.DIG_ERROR
        }
        if (!canBuild(entity.location)) {
            return Messages.DIG_ANTI_BUILD_ERROR
        }

        // ブロックを壊す
        breakBlock(ret)
        return null
    }

    @HostAccess.Export
    override fun plantX(x: Int, y: Int, z: Int, cord: String): Boolean {
        Bukkit.getLogger().info("plantX by $_selectedSlotNo")
        return _taskRunner.executeWithResult<Boolean>({
            val location = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
            val targetBlock = location.block

            var itemStack = _inventory.selectItem(_selectedSlotNo)
            if (itemStack == null || !ItemStackUtil.isCrop(itemStack) && !ItemStackUtil.isPlantable(itemStack) && !ItemStackUtil.isSapling(
                    itemStack
                )
            ) {
                itemStack = _inventory.findPlantItem()
            }

            if (itemStack != null) {
                //backup
                _saveArea?.addChangeBlocks(mutableListOf(targetBlock))
                val actionPerformed = when {
                    ItemStackUtil.isCrop(itemStack) -> GardeningUtil.sowing(targetBlock, itemStack)
                    ItemStackUtil.isPlantable(itemStack) -> GardeningUtil.planting(targetBlock, itemStack)
                    ItemStackUtil.isSapling(itemStack) -> ForestryUtil.plant(targetBlock, itemStack)
                    else -> false
                }

                if (actionPerformed) {
                    entity.swingMainHand()
                    if (!isCreativeMode) _inventory.removeItem(ItemStack(itemStack.type, 1))
                    return@executeWithResult true
                }
                //なにもしなかった場合は正常終了でfalseを返す
                return@executeWithResult false
            }
            playFailSound()
            _entityUtil.displayChatBubble(Messages.USE_ERROR_PLANT)
            return@executeWithResult false
        }, period).get()
    }

    @HostAccess.Export
    fun plant(): Boolean {
        return plantX(0, -1, 0, "^")
    }

    @HostAccess.Export
    override fun tillX(x: Int, y: Int, z: Int, cord: String): Boolean {
        Bukkit.getLogger().info("tillX by $_selectedSlotNo")
        return _taskRunner.executeWithResult<Boolean>({
            val itemStack = _inventory.selectItemName(_selectedSlotNo, "hoe")
            if (itemStack != null) {
                val location = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
                val targetBlock = location.block
                if (ItemStackUtil.isHoe(itemStack)) {
                    //指定されたブロックを耕すことができればたがやす
                    //backup
                    _saveArea?.addChangeBlocks(mutableListOf(targetBlock))
                    if (BlockUtil.plowField(targetBlock)) {
                        entity.swingMainHand()
                        val blockChangedEvent = BlockChangedEvent(targetBlock.location, listOf(targetBlock))
                        server.pluginManager.callEvent(blockChangedEvent)
                        return@executeWithResult true
                    }
                    //なにもしなかった場合は正常終了でfalseを返す
                    return@executeWithResult false
                }
            }

            playFailSound()
            _entityUtil.displayChatBubble(Messages.USE_ERROR_HOE);
            return@executeWithResult false
        }, period).get()
    }

    @HostAccess.Export
    fun till(): Boolean {
        return tillX(0, -1, 0, "^")
    }

    @HostAccess.Export
    override fun flattenX(x: Int, y: Int, z: Int, cord: String): Boolean {
        Bukkit.getLogger().info("useShovelX by $_selectedSlotNo")
        return _taskRunner.executeWithResult<Boolean>({
            val itemStack = _inventory.selectItemName(_selectedSlotNo, "shovel")

            if (itemStack != null) {
                val location = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
                val targetBlock = location.block
                if (ItemStackUtil.isShovel(itemStack)) {
                    //指定されたブロックを平らにすることができたら平らにする
                    //backup
                    _saveArea?.addChangeBlocks(mutableListOf(targetBlock))
                    if (BlockUtil.flattenGround(targetBlock)) {
                        entity.swingMainHand()
                        val blockChangedEvent = BlockChangedEvent(targetBlock.location, listOf(targetBlock))
                        server.pluginManager.callEvent(blockChangedEvent)
                        return@executeWithResult true
                    }
                    //なにもしなかった場合は正常終了でfalseを返す
                    return@executeWithResult false
                }
            }

            playFailSound()
            _entityUtil.displayChatBubble(Messages.USE_ERROR_SHAVEL);
            return@executeWithResult false
        }, period).get()
    }

    @HostAccess.Export
    fun flatten(): Boolean {
        return flattenX(0, -1, 0, "^")
    }


    @HostAccess.Export
    override fun digX(x: Int, y: Int, z: Int, cord: String): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val error = smartDig(x, y, z, cord)
            if (error != null) {
                _entityUtil.displayChatBubble(error);
                return@executeWithResult false
            }
            return@executeWithResult true
        }, period).get()
    }

    @HostAccess.Export
    fun harvest(): Boolean {
        return digX(0, 0, 0, "^")
    }

    @HostAccess.Export
    override fun dig(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            var error: String? = null
            val deep = if (isCreativeMode) 1 else 8
            for (i in 1..deep) {
                val ret = smartDig(0, 0, i, "^")
                if (ret == null) {
                    return@executeWithResult true
                } else {
                    error = ret
                    if (error == Messages.DIG_ANTI_BUILD_ERROR) break
                }
            }
            if (error != null) {
                _entityUtil.displayChatBubble(error)
            }
            return@executeWithResult false
        }, period).get()
    }

    @HostAccess.Export
    override fun digUp(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            var error: String? = null
            val deep = if (isCreativeMode) 1 else 8
            for (i in 1..deep) {
                val ret = smartDig(0, i, 0, "^")
                if (ret == null) {
                    return@executeWithResult true
                } else {
                    error = ret
                    if (error == Messages.DIG_ANTI_BUILD_ERROR) break
                }
            }
            if (error != null) {
                _entityUtil.displayChatBubble(error)
            }
            return@executeWithResult false
        }, period).get()
    }

    @HostAccess.Export
    override fun digDown(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            var error: String? = null
            val deep = if (isCreativeMode) 1 else 8
            for (i in 1..deep) {
                val ret = smartDig(0, -i, 0, "^")
                if (ret == null) {
                    return@executeWithResult true
                } else {
                    error = ret
                    if (error == Messages.DIG_ANTI_BUILD_ERROR) break
                }
            }
            if (error != null) {
                _entityUtil.displayChatBubble(error)
            }
            return@executeWithResult false
        }, period).get()
    }

    private fun isCanDigDeep(x: Int, y: Int, z: Int, cord: String): Boolean {
        val newLocation = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
        val ret = newLocation.block
        if (!BlockUtil.canBreakableBlock(ret)) {
            return false
        }
        if (!canBuild(entity.location)) {
            return false
        }

        return true
    }

    @HostAccess.Export
    override fun canDig(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val deep = if (isCreativeMode) 1 else 8
            for (i in 1..deep) {
                val ret = isCanDigDeep(0, 0, i, "^")
                if (ret) {
                    return@executeWithResult true
                }
            }
            return@executeWithResult false
        }, 0).get()
    }

    @HostAccess.Export
    override fun canDigUp(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val deep = if (isCreativeMode) 1 else 8
            for (i in 1..deep) {
                val ret = isCanDigDeep(0, i, 0, "^")
                if (ret) {
                    return@executeWithResult true
                }
            }
            return@executeWithResult false
        }, 0).get()
    }

    @HostAccess.Export
    override fun canDigDown(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            isCanDigDeep(0, -1, 0, "^")
        }, 0).get()
    }

    @HostAccess.Export
    override fun isBlocked(): Boolean {
        return _taskRunner.executeWithResult({
            val newLocation = BlockUtil.getLocation(entity.location, 0, 0, 1)
            !canMove(newLocation)
        }, 0).get()
    }

    @HostAccess.Export
    override fun isBlockedDown(): Boolean {
        return _taskRunner.executeWithResult({
            val newLocation = BlockUtil.getLocation(entity.location, 0, -1, 0)
            !canMove(newLocation)
        }, 0).get()
    }

    @HostAccess.Export
    override fun isBlockedUp(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val newLocation = BlockUtil.getLocation(entity.location, 0, 1, 0)
            !canMove(newLocation)
        }, 0).get()
    }

    @HostAccess.Export
    override fun isBlockedAt(x: Int, y: Int, z: Int, cord: String): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            val newLocation = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
            !canMove(newLocation)
        }, 0).get()
    }

    @HostAccess.Export
    override fun getDistance(): Double {
        return _taskRunner.executeWithResult({
            val world = entity.location.world ?: return@executeWithResult -1.0
            val start = entity.location.clone()
            val direction = entity.location.direction.normalize()

            val filter = Predicate<Entity> { e -> e != entity }
            val rayTrace =
                world.rayTrace(start.add(0.0, 0.5, 0.0), direction, 30.0, FluidCollisionMode.NEVER, true, 0.3, filter)
            rayTrace?.hitPosition?.distance(start.toVector()) ?: -1.0
        }, 0).get() // ここでの例外はtry-catchで捕捉
    }

    @HostAccess.Export
    override fun getDistanceUp(): Double {
        return _taskRunner.executeWithResult({
            val world = entity.location.world ?: return@executeWithResult -1.0
            val start = entity.location.clone()
            val upDirection = Vector(0.0, 1.0, 0.0) // 上方向へのベクトル

            val filter = Predicate<Entity> { e -> e != entity }
            val rayTrace = world.rayTrace(start, upDirection, 30.0, FluidCollisionMode.NEVER, true, 0.3, filter)
            rayTrace?.hitPosition?.distance(start.toVector()) ?: -1.0
        }, 0).get() // ここでの例外はtry-catchで捕捉
    }

    @HostAccess.Export
    override fun getDistanceDown(): Double {
        return _taskRunner.executeWithResult({
            val world = entity.location.world ?: return@executeWithResult -1.0
            val start = entity.location.clone()
            val upDirection = Vector(0.0, -1.0, 0.0) // 下方向へのベクトル

            val filter = Predicate<Entity> { e -> e != entity }
            val rayTrace = world.rayTrace(start, upDirection, 30.0, FluidCollisionMode.NEVER, true, 0.3, filter)
            rayTrace?.hitPosition?.distance(start.toVector()) ?: -1.0
        }, 0).get() // ここでの例外はtry-catchで捕捉
    }

    @HostAccess.Export
    override fun getTargetDistance(uuid: String): Double {
        if (uuid.isEmpty()) return -1.0
        try {
            return _taskRunner.executeWithResult({
                Bukkit.getEntity(UUID.fromString(uuid))?.let { target ->
                    if (entity.world == target.world) {
                        target.location.distance(entity.location)
                    } else {
                        // 異なるワールドの場合の処理
                        Double.MAX_VALUE
                    }
                }
            }, 0).get() ?: -1.0 // ここでの例外はtry-catchで捕捉
        } catch (e: Exception) {
            // 例外処理（ログ記録、適切なデフォルト値の返却など）
            e.printStackTrace()
            return -1.0 // 例えば最大値を返す
        }
    }

    @HostAccess.Export
    override fun makeSound() {
        _taskRunner.execute({
            _entityUtil.playEntitySound()
        }, period)
    }

    @HostAccess.Export
    override fun attackTarget(uuid: String) {
        if (entity is Mob) {
            val mob: Mob = entity
            _taskRunner.execute({
                val target = Bukkit.getEntity(UUID.fromString(uuid))
                if (target != null && target is LivingEntity) mob.target = target
            }, period)
        }
    }

    @HostAccess.Export
    override fun attack(): Boolean {
        val itemStack = _inventory.selectItem(_selectedSlotNo)
        if (ItemStackUtil.isRangedWeapon(itemStack)) {
            return attackX(0, 0, 1, "^")
        }

        return _taskRunner.executeWithResult({
            val target = _entityUtil.newLocation(0.0, 0.0, 1.0, "^")

            // 基本ダメージ計算
            val baseDamage = if (itemStack != null) DamageCalculator.calculateBaseDamage(itemStack.type) else 1.0

            // エンチャントの計算
            val enchantments = itemStack?.enchantments ?: mapOf()
            val enchantmentDamage = DamageCalculator.calculateEnchantmentDamage(enchantments)

            // 最終ダメージの計算
            val finalDamage = baseDamage + enchantmentDamage

            entity.world.getNearbyEntities(target, 0.5, 0.5, 0.5)
                .filterIsInstance<LivingEntity>()
                .filter { it != entity }
                .forEach {
                    if (_entityRepository.findById(it.uniqueId) != null) {
                        val direction = it.location.toVector().subtract(entity.location.toVector()).normalize()
                        val power = Vector(direction.x * 0.5, 0.1, direction.z * 0.5)  // 吹き飛ばしの方向と強さ
                        it.velocity = it.velocity.add(power)
                        val sound = it.hurtSound ?: Sound.ENTITY_PIG_HURT
                        it.world.playSound(it.location, sound, SoundCategory.NEUTRAL, 1.0f, 1.0f)
                        //it.world.spawnParticle(Particle.VILLAGER_ANGRY, it.location, 20, 0.5, 0.5, 0.5, 0.1)
                    } else {
                        it.damage(finalDamage, entity)
                    }
                }
            entity.swingMainHand()
            entity.world.playSound(entity.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f)


            return@executeWithResult true
        }, period).get()
    }

    @HostAccess.Export
    override fun attackX(x: Int, y: Int, z: Int, cord: String): Boolean {
        return _taskRunner.executeWithResult({
            val target = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
            val direction = target.subtract(entity.location).toVector().normalize()
            val origin = Location(entity.location.world, 0.0, 0.0, 0.0) // ワールドと座標を指定してLocationオブジェクトを作成する
            origin.direction = direction // Locationオブジェクトの方向をVectorオブジェクトに設定する
            val yaw = origin.yaw // yawを取得する
            val pitch = origin.pitch // pitchを取得す

            val location: Location = entity.location // Entityの現在の座標を取得
            val newLocation =
                Location(location.world, location.x, location.y, location.z, yaw, pitch) // yaw, pitchを0に設定
            updateLocation(newLocation)

            val itemStack = _inventory.selectArrow(_selectedSlotNo) ?: return@executeWithResult false

            // 基本ダメージ計算
            val baseDamage = DamageCalculator.calculateBaseDamage(itemStack.type)

            // エンチャントの計算
            val enchantments = itemStack.enchantments
            val enchantmentDamage = DamageCalculator.calculateEnchantmentDamage(enchantments)

            // 最終ダメージの計算
            val finalDamage = baseDamage + enchantmentDamage

            // 矢を発射
            val arrow: Arrow = entity.launchProjectile(Arrow::class.java)

            // 矢の速度と角度を設定（任意に調整可能）
            val arrowVelocity: Vector = entity.location.direction.multiply(2.0)
            arrow.velocity = arrowVelocity

            entity.swingMainHand()
            entity.world.playSound(entity.location, Sound.ENTITY_ARROW_SHOOT, 1f, 1f)

            // インベントリから矢を1つ消費
            if (!isCreativeMode) {
                val arrowItemStack = ItemStack(Material.ARROW, 1)
                inventory.removeItem(arrowItemStack)
            }

            return@executeWithResult true
        }, period * 3).get()
    }

    @HostAccess.Export
    fun inspectX(x: Int, y: Int, z: Int, cord: String): Any? {
        return toProxyObject(inspectXMap(x, y, z, cord))
    }

    @HostAccess.Export
    fun inspectX(x: Int, y: Int, z: Int): Any? {
        return inspectX(x, y, z, "^")
    }

    @HostAccess.Export
    fun inspect(): Any? {
        return inspectX(0, 0, 0, "^")
    }

    @HostAccess.Export
    fun inspectUp(): Any? {
        return inspectX(0, 1, 0, "^")
    }

    @HostAccess.Export
    fun inspectDown(): Any? {
        return inspectX(0, -1, 0, "^")
    }

    override fun inspectXMap(x: Int, y: Int, z: Int, cord: String): Map<String, Any?> {
        return _taskRunner.executeWithResult({
            val newLocation = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
            BlockUtil.toMapData(newLocation)
        }, 0).get()
    }


    @HostAccess.Export
    fun getBlock(x: Int, y: Int, z: Int): Any? {
        return toProxyObject(getBlockMap(x, y, z))
    }

    override fun getBlockMap(x: Int, y: Int, z: Int): Map<String, Any?> {
        return _taskRunner.executeWithResult({
            val newLocation = Location(entity.world, x.toDouble(), y.toDouble(), z.toDouble())
            BlockUtil.toMapData(newLocation)
        }, 0).get()
    }

    @HostAccess.Export
    fun scan(x: Int, y: Int, z: Int): Any? {
        return toProxyObject(scanMap(x, y, z))
    }

    override fun scanMap(x: Int, y: Int, z: Int): List<Map<String, Any?>> {
        return _taskRunner.executeWithResult({
            val list = entity.getNearbyEntities(x.toDouble(), y.toDouble(), z.toDouble())

            list.map { it ->
                EntityUtil(it as LivingEntity).toMapData()
            }.toList()
        }, 0).get()
    }

    override fun stop() {
        _taskRunner.execute({
            _entityUtil.stay()
        }, period)
    }

    private fun sendMessage(player: Player?, message: String) {
        // プレイヤーにメッセージを送信
        if (player != null) {
            player.sendMessage(ChatUtil.normalMessage(message))
        }
    }

    @HostAccess.Export
    override fun say(vararg messages: Any?) {
        _taskRunner.execute({
            val joinedMessage = messages.joinToString(separator = " ") { it.toString() }
            val formattedMessage = ChatUtil.normalMessage("<${getName()}> $joinedMessage")
            entity.getNearbyEntities(30.0, 30.0, 30.0).forEach {
                if (it is Player) { // エンティティが話しかけられるかどうかのチェック
                    sendMessage(it, formattedMessage)
                }
            }

            //自分を含めて近くのEntityにChatイベントを送信
            _entityRepository.findById(uuid)?.let {
                val callEvent = EventData(
                    "onEntityChat", mutableMapOf<String, Any>(
                        "entityUuid" to entity.uniqueId.toString(),
                        "message" to joinedMessage,
                    )
                )
                webHandlerService.nearbylocation(WebHandlerType.RENDER, entity.location).forEach {
                    it.sendEvent(callEvent)
                }
            }

            if (joinedMessage.length < 40) {
                _entityUtil.displayChatBubble(joinedMessage, 30L)
            }
        }, 30L)
    }

    @HostAccess.Export
    override fun report(message: String) {
        val formattedMessage = ChatUtil.normalMessage("<${getName()}> $message")
        _entityRepository.findById(uuid)?.let {
            sendMessage(Bukkit.getPlayer(it.ownerUuid), formattedMessage)
        }
    }

    @HostAccess.Export
    override fun whisper(player: String, message: String) {
        val formattedMessage = ChatUtil.normalMessage("<${getName()}> $message")
        PlayerUtil.getPlayers(player).forEach {
            if (it.location.distance(entity.location) < 10.0) {
                lookAt(it.location.x, it.location.y, it.location.z, "")
            }
            _taskRunner.execute({
                sendMessage(it, formattedMessage)
            }, period)
        }
    }

    @HostAccess.Export
    override fun distanceX(x: Double, y: Double, z: Double, cord: String): Double {
        val location = _entityUtil.newLocation(x, y, z, cord)
        return entity.location.distance(location)
    }

    @HostAccess.Export
    override fun lookAt(x: Double, y: Double, z: Double, cord: String): Boolean {
        return _taskRunner.executeWithResult({
            val location = _entityUtil.newLocation(x, y, z, cord)
            _entityUtil.lookAt(location.x, location.y, location.z)
            true
        }, 0).get()
    }

    @HostAccess.Export
    override fun lookAtTarget(uuid: String): Boolean {
        if (uuid.isEmpty()) return false
        return _taskRunner.executeWithResult({
            Bukkit.getEntity(UUID.fromString(uuid))?.let { entity ->
                _entityUtil.lookAt(entity.location.x, entity.location.y, entity.location.z)
                true
            } ?: false
        }, 0).get()
    }


    @HostAccess.Export
    override fun lookAtOther() {
        _taskRunner.execute({
            val ownerUuid = _entityRepository.findById(entity.uniqueId)?.ownerUuid
            Bukkit.getEntity(uuid)?.let { entity ->
                entity.getNearbyEntities(30.0, 30.0, 30.0).forEach {
                    if (it.uniqueId != uuid && it.uniqueId != ownerUuid) {
                        _entityUtil.lookAt(it.location.x, it.location.y, it.location.z)
                        return@execute
                    }
                }
            }
        }, 0)
    }

    @HostAccess.Export
    override fun swapItem(slot1: Int, slot2: Int): Boolean {
        return _taskRunner.executeWithResult({
            _inventory.swapItem(slot1, slot2)
        }, 0).get()
    }

    @HostAccess.Export
    override fun moveItem(slot1: Int, slot2: Int): Boolean {
        return _taskRunner.executeWithResult({
            _inventory.moveItem(slot1, slot2)
        }, 0).get()
    }

    @HostAccess.Export
    override fun dropItem(slot: Int): Boolean {
        return _taskRunner.executeWithResult({
            val itemStack = _inventory.getItem(slot) ?: return@executeWithResult false
            ItemStackUtil.dropItem(entity, itemStack)
            _inventory.clear(slot)
            return@executeWithResult true
        }, period).get()
    }

    @HostAccess.Export
    override fun passItem(slot: Int, target: String): Boolean {
        return _taskRunner.executeWithResult({
            val itemStack = _inventory.getItem(slot) ?: return@executeWithResult false
            if (target.isEmpty()) {
                // プレイヤーがそもそもブランクならそのまま足元に落とす
                entity.world.dropItem(entity.location, itemStack) // エンティティの足元にアイテムを落とす
                _inventory.removeItem(itemStack) // エンティティのインベントリーからアイテムを削除
                return@executeWithResult false
            }
            Bukkit.getPlayer(target)?.let { player ->
                player.sendMessage(ChatUtil.systemMessage("${getName()}はあなたにアイテム${itemStack.type.name.lowercase()}を${itemStack.amount}個渡しました"));
                val inventory = player.inventory
                if (inventory.firstEmpty() != -1) { // プレイヤーのインベントリーが満杯かどうかを確認
                    inventory.addItem(itemStack) // プレイヤーのインベントリーにアイテムを追加
                    _inventory.removeItem(itemStack) // エンティティのインベントリーからアイテムを削除
                    return@executeWithResult true
                } else {
                    player.world.dropItem(player.location, itemStack) // プレイヤーの足元にアイテムを落とす
                    _inventory.removeItem(itemStack) // エンティティのインベントリーからアイテムを削除
                    return@executeWithResult false
                }
            } ?: run {// プレイヤーが見つからない場合
                entity.world.dropItem(entity.location, itemStack) // エンティティの足元にアイテムを落とす
                _inventory.removeItem(itemStack) // エンティティのインベントリーからアイテムを削除
                return@executeWithResult false
            }
        }, period).get()
    }

    @HostAccess.Export
    override fun findSlot(itemName: String): Int {
        return _taskRunner.executeWithResult({
            return@executeWithResult _inventory.findSlot(itemName)
        }, period).get()
    }

    override fun findInventory(location: Location): Inventory? {
        return BlockUtil().findInventory(location)
    }

    override fun findEntityInventory(uuid: UUID): Inventory? {
        return BlockUtil().findEntityInventory(entity.world, uuid)
    }

    @HostAccess.Export
    fun getInventoryX(x: Double, y: Double, z: Double, cord: String): Any? {
        return toProxyObject(getInventoryXMap(x, y, z, cord))
    }

    override fun getInventoryXMap(x: Double, y: Double, z: Double, cord: String): Map<String, Any> {
        return _taskRunner.executeWithResult<Map<String, Any>>({
            val location = _entityUtil.newLocation(x, y, z, cord)
            val iv = findInventory(location)
            return@executeWithResult mapOf(
                "world" to location.world!!.name,
                "x" to location.blockX,
                "y" to location.blockY,
                "z" to location.blockZ,
                "size" to (iv?.size ?: 0),
                "items" to (iv?.contents?.map { itemStack ->
                    itemStack?.let {
                        mapOf(
                            "type" to itemStack.type.name.lowercase(),
                            "amount" to itemStack.amount
                        )
                    }
                } ?: emptyList())
            )
        }, period).get()
    }


    @HostAccess.Export
    override fun putToChestX(x: Int, y: Int, z: Int, cord: String): Boolean {
        return _taskRunner.executeWithResult({
            val location = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
            return@executeWithResult _inventory.storeToInventory(location)
        }, period).get()
    }

    @HostAccess.Export
    fun putToChest(): Boolean {
        return _taskRunner.executeWithResult({
            putToChestX(0, 0, 1, "^")
        }, period).get()
    }

    @HostAccess.Export
    fun putToChestDown(): Boolean {
        return _taskRunner.executeWithResult({
            putToChestX(0, -1, 0, "^")
        }, period).get()
    }

    @HostAccess.Export
    fun putToChestUp(): Boolean {
        return _taskRunner.executeWithResult({
            putToChestX(0, 1, 0, "^")
        }, period).get()
    }

    @HostAccess.Export
    override fun takeFromChestX(x: Int, y: Int, z: Int, cord: String): Boolean {
        return _taskRunner.executeWithResult({
            val location = _entityUtil.newLocation(x.toDouble(), y.toDouble(), z.toDouble(), cord)
            return@executeWithResult _inventory.retrieveFromChest(location)
        }, period).get()
    }

    @HostAccess.Export
    fun takeFromChest(): Boolean {
        return _taskRunner.executeWithResult({
            takeFromChestX(0, 0, 1, "^")
        }, period).get()
    }

    @HostAccess.Export
    fun takeFromChestDown(): Boolean {
        return _taskRunner.executeWithResult({
            takeFromChestX(0, -1, 0, "^")
        }, period).get()
    }

    @HostAccess.Export
    fun takeFromChestUp(): Boolean {
        return _taskRunner.executeWithResult<Boolean>({
            takeFromChestX(0, 1, 0, "^")
        }, period).get()
    }


    @HostAccess.Export
    override fun pickupItemsX(x: Double, y: Double, z: Double, cord: String): Int {
        return _taskRunner.executeWithResult({
            val location = _entityUtil.newLocation(x, y, z, cord)
            val items = entity.world.getNearbyEntities(location, 2.0, 2.0, 2.0)
                .filterIsInstance<Item>()
                .filter { it.itemStack.type != Material.AIR }
            var count = 0
            if (items.isNotEmpty()) {
                for (item in items) {
                    _inventory.addItem(item.itemStack)
                    item.remove()
                    count++
                }
            }
            count
        }, period).get()
    }

    @HostAccess.Export
    override fun pickupItems(): Int {
        return pickupItemsX(0.0, 0.0, 0.0, "^")
    }

    @HostAccess.Export
    override fun executeCommand(command: String): Boolean {
        return _taskRunner.executeWithResult({
            _entityRepository.findById(uuid)?.let { namedEntity ->
                Bukkit.getPlayer(namedEntity.ownerUuid)?.let {
                    it.performCommand(command)
                    true // プレイヤーが存在する場合はtrueを返す
                } ?: false // プレイヤーが存在しない場合はfalseを返す
            } ?: false
        }, period).get()
    }

    override fun addEventMessage(event: Map<String, Any?>) {
        Bukkit.getLogger().info("***** addEventMessage Length: now: ${eventList.size}　add ${event.toString()}")
        eventList.add(event)
    }

    @HostAccess.Export
    override fun getEventMessage(): Any? {
        if (eventList.isEmpty()) {
            return null
        }
        return toProxyObject(getEventMessageXMap())
    }

    override fun getEventMessageXMap(): Map<String, Any?> {
        Bukkit.getLogger().info("***** getEventMessage Length: ${eventList.size}")
        if (eventList.isEmpty()) {
            return emptyMap()
        }
        return eventList.removeFirst()
    }
}
