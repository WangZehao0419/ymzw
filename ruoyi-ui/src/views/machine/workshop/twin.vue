<template>
  <div v-loading="loading" element-loading-text="正在加载车间数据" element-loading-background="rgba(11, 15, 20, 0.85)" class="twin-page">
    <!-- 顶栏：返回 / 标题 / 网格吸附开关 / 保存布局 -->
    <div class="twin-header">
      <el-button size="small" icon="el-icon-back" @click="goBack">返回</el-button>
      <div class="twin-title">车间孪生编辑 · {{ workshop.workshopName || '加载中' }}</div>
      <div class="twin-actions">
        <span class="snap-text">网格吸附 0.5m</span>
        <el-switch v-model="snapOn" active-color="#409EFF" inactive-color="#2a3644" />
        <el-button
          size="small"
          :type="dirty ? 'warning' : 'primary'"
          :loading="saving"
          @click="saveLayout"
        >保存布局<span v-if="dirty" class="dirty-dot"></span></el-button>
      </div>
    </div>

    <div class="twin-body">
      <!-- 左栏：设备清单，分「已摆放 / 未摆放」两组 -->
      <div class="twin-left">
        <div class="panel-title">设备清单</div>
        <div class="panel-sub">已摆放 · {{ placedList.length }}</div>
        <div
          v-for="item in placedList"
          :key="item.id"
          class="device-item"
          :class="{ active: isDeviceMode && Number(selectedEquipmentId) === Number(item.id) }"
          title="点击选中（同步 3D 高亮）"
          @click="selectEquipment(item.id)"
        >
          <span class="status-dot" :style="{ background: statusColorCss(item.equipmentStatus) }"></span>
          <span class="device-no">{{ item.equipmentNo }}</span>
          <span class="device-name">{{ item.equipmentName }}</span>
        </div>
        <div v-if="!placedList.length" class="empty-tip">暂无已摆放设备</div>
        <div class="panel-sub">未摆放 · {{ unplacedList.length }}</div>
        <div
          v-for="item in unplacedList"
          :key="item.id"
          class="device-item unplaced"
          draggable="true"
          title="拖拽到 3D 画布摆放；双击自动放到画布中心空闲处"
          @dragstart="onDragStart($event, item)"
          @dblclick="placeAtFreeSpot(item)"
        >
          <span class="status-dot" :style="{ background: statusColorCss(item.equipmentStatus) }"></span>
          <span class="device-no">{{ item.equipmentNo }}</span>
          <span class="device-name">{{ item.equipmentName }}</span>
        </div>
        <div v-if="!unplacedList.length" class="empty-tip">设备已全部摆放</div>
      </div>

      <!-- 中部：3D 画布（拖拽落点容器） -->
      <div ref="canvas" class="twin-canvas" @dragover.prevent @drop.prevent="handleDrop">
        <div class="canvas-hint">左键拖拽设备移动 · 空白处拖拽旋转视角 · 滚轮缩放 · 点击设备编辑属性</div>
      </div>

      <!-- 右栏：属性面板（设备 / 车间两种模式互斥） -->
      <div class="twin-right">
        <div class="panel-title">{{ isDeviceMode ? '设备属性' : '车间属性' }}</div>
        <div class="panel-tip">{{ isDeviceMode ? '修改后需点击「保存属性」提交' : '点击画布空白处切换到车间属性；点击设备切换到设备属性' }}</div>

        <el-form v-if="isDeviceMode" ref="deviceFormRef" :model="deviceForm" :rules="deviceRules" label-width="80px" size="small" class="prop-form">
          <el-form-item label="设备编号">
            <el-input v-model="deviceForm.equipmentNo" disabled />
          </el-form-item>
          <el-form-item label="设备名称" prop="equipmentName">
            <el-input v-model="deviceForm.equipmentName" placeholder="请输入设备名称" />
          </el-form-item>
          <el-form-item label="设备型号" prop="equipmentModelName">
            <el-input v-model="deviceForm.equipmentModelName" placeholder="请输入设备型号" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="deviceForm.equipmentStatus" style="width: 100%">
              <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="deviceForm.equipmentRemark" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="布局坐标">
            <div class="coord-text">X: {{ fmtCoord(selectedEquipment && selectedEquipment.layoutX) }} / Y: {{ fmtCoord(selectedEquipment && selectedEquipment.layoutY) }}</div>
          </el-form-item>
        </el-form>
        <div v-if="isDeviceMode" class="form-btns">
          <el-button size="small" type="primary" :loading="savingProp" @click="saveDevice">保存属性</el-button>
          <el-button size="small" type="danger" plain @click="moveBackToList">移回清单</el-button>
        </div>

        <el-form v-else ref="workshopFormRef" :model="workshopForm" :rules="workshopRules" label-width="80px" size="small" class="prop-form">
          <el-form-item label="车间名称" prop="workshopName">
            <el-input v-model="workshopForm.workshopName" placeholder="请输入车间名称" />
          </el-form-item>
          <el-form-item label="位置">
            <el-input v-model="workshopForm.workshopLocation" placeholder="请输入位置" />
          </el-form-item>
          <el-form-item label="负责人">
            <el-input v-model="workshopForm.workshopManager" placeholder="请输入负责人" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="workshopForm.workshopStatus" style="width: 100%">
              <el-option v-for="s in workshopStatusOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="workshopForm.workshopRemark" type="textarea" :rows="3" />
          </el-form-item>
        </el-form>
        <div v-if="!isDeviceMode" class="form-btns">
          <el-button size="small" type="primary" :loading="savingProp" @click="saveWorkshopProp">保存属性</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { listEquipment, updateEquipment, getWorkshop, updateWorkshop, saveWorkshopLayout } from '@/api/business/machine'

// 设备状态 → 颜色（与设备台账一致：0 运行 / 1 待机 / 2 维护 / 3 离线），3D 与左栏小点共用同一份映射
const STATUS_COLORS = { '0': '#67C23A', '1': '#909399', '2': '#E6A23C', '3': '#303133' }
// 车间固定尺寸 24m(x) × 16m(z)；摆放活动范围各内缩 0.5m，防止设备贴墙穿模
const CLAMP_X = 11.5
const CLAMP_Z = 7.5
const SNAP_STEP = 0.5
// pointer 位移小于该像素值视为「点击」而非「拖拽」，避免点选设备时产生轻微位移抖动
const CLICK_SLOP_PX = 5

export default {
  name: 'BusinessWorkshopTwin',
  data() {
    return {
      loading: false,
      saving: false,
      savingProp: false,
      // 布局是否有未保存的修改（拖拽 / 拖放 / 移回清单都会置位，保存成功后复位）
      dirty: false,
      snapOn: true,
      workshopId: null,
      workshop: {},
      workshopForm: {},
      equipmentList: [],
      // null = 车间模式（未选中任何设备），否则为选中设备 id
      selectedEquipmentId: null,
      deviceForm: {},
      statusOptions: [{ label: '运行中', value: '0' }, { label: '待机', value: '1' }, { label: '维护中', value: '2' }, { label: '离线', value: '3' }],
      workshopStatusOptions: [{ label: '启用', value: '0' }, { label: '停用', value: '1' }],
      deviceRules: {
        equipmentName: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
        equipmentModelName: [{ required: true, message: '设备型号不能为空', trigger: 'blur' }]
      },
      workshopRules: {
        workshopName: [{ required: true, message: '车间名称不能为空', trigger: 'blur' }]
      }
    }
  },
  computed: {
    placedList() {
      return this.equipmentList.filter(it => it.layoutX != null && it.layoutY != null)
    },
    unplacedList() {
      return this.equipmentList.filter(it => it.layoutX == null || it.layoutY == null)
    },
    isDeviceMode() {
      return this.selectedEquipmentId != null
    },
    // 布局坐标显示取「活数据」而非表单快照：拖拽过程中右侧面板坐标实时跟随
    selectedEquipment() {
      return this.equipmentList.find(it => Number(it.id) === Number(this.selectedEquipmentId)) || null
    }
  },
  created() {
    // 路由守卫：缺少 workshopId 时无法编辑，回落到车间列表
    const workshopId = Number(this.$route.query.workshopId)
    if (!this.$route.query.workshopId || !workshopId) {
      this.$message.warning('缺少车间参数')
      this.$router.replace('/machine-business/workshop')
      return
    }
    this.workshopId = workshopId
    this.loading = true
    Promise.all([
      getWorkshop(workshopId),
      listEquipment({ pageNum: 1, pageSize: 100, workshopId })
    ]).then(([workshop, res]) => {
      this.workshop = workshop || {}
      this.workshopForm = { ...this.workshop }
      // 含全部字段副本：左栏分组、右栏表单、保存布局的载荷都来源于此。
      // 坐标显式规范化为 Number|null：Vue2 无法侦测「后新增的属性」，必须保证 layoutX/layoutY 键一开始就存在，
      // 否则拖放/移回清单时左栏分组与 dirty 状态不会响应式刷新
      this.equipmentList = (res.rows || []).map(it => ({
        ...it,
        layoutX: it.layoutX == null ? null : Number(it.layoutX),
        layoutY: it.layoutY == null ? null : Number(it.layoutY)
      }))
      this.$nextTick(() => {
        this.initThree()
        this.loading = false
      })
    }).catch(() => {
      this.loading = false
      this.$message.error('车间数据加载失败')
    })
  },
  // dirty 状态下离开页面前确认，防止误触丢失未保存的布局修改
  beforeRouteLeave(to, from, next) {
    if (this.dirty) {
      this.$modal.confirm('布局尚未保存，离开将丢失未保存的修改，是否继续？').then(() => next()).catch(() => next(false))
    } else {
      next()
    }
  },
  beforeDestroy() {
    this.destroyThree()
  },
  methods: {
    /* ---------- 通用 ---------- */
    statusColorCss(status) {
      return STATUS_COLORS[String(status)] || '#909399'
    },
    fmtCoord(v) {
      return v == null ? '-' : Number(v).toFixed(1)
    },
    findEquipment(id) {
      return this.equipmentList.find(it => Number(it.id) === Number(id))
    },
    goBack() {
      this.$router.push('/machine-business/workshop')
    },

    /* ---------- 选中与面板联动 ---------- */
    selectEquipment(id) {
      const item = this.findEquipment(id)
      if (!item) return
      this.selectedEquipmentId = Number(id)
      // 表单做快照：未点「保存属性」前的编辑不会直接污染列表数据
      this.deviceForm = {
        ...item,
        // 状态统一转字符串，避免后端返回数字时 el-select 匹配不到选项
        equipmentStatus: item.equipmentStatus == null ? '' : String(item.equipmentStatus)
      }
      this.setSelection(this.meshMap ? this.meshMap[Number(id)] : null)
    },
    selectWorkshop() {
      this.selectedEquipmentId = null
      this.workshopForm = { ...this.workshop }
      this.setSelection(null)
    },

    /* ---------- 3D 场景 ---------- */
    initThree() {
      const container = this.$refs.canvas
      if (!container) return
      const width = container.clientWidth || 800
      const height = container.clientHeight || 600

      const scene = new THREE.Scene()
      scene.background = new THREE.Color(0x0b0f14)
      const camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 200)
      camera.position.set(16, 13, 16)
      camera.lookAt(0, 0, 0)
      const renderer = new THREE.WebGLRenderer({ antialias: true })
      renderer.setPixelRatio(window.devicePixelRatio || 1)
      renderer.setSize(width, height)
      container.appendChild(renderer.domElement)

      // three 对象挂实例属性而不放 data()：避免 Vue2 递归做响应式代理拖慢大对象
      this.scene = scene
      this.camera = camera
      this.renderer = renderer
      this.raycaster = new THREE.Raycaster()
      // 拖拽投影用的数学平面（车间地面 y=0）
      this.groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0)
      this.meshMap = {}
      this.pickMeshes = []

      // 灯光：环境光打底 + 平行光塑形，弱化暗部死黑
      scene.add(new THREE.AmbientLight(0xffffff, 0.55))
      const dirLight = new THREE.DirectionalLight(0xffffff, 0.85)
      dirLight.position.set(10, 18, 8)
      scene.add(dirLight)

      this.buildWorkshop()

      // 已有布局的设备直接建 mesh；layoutY 存的是平面纵坐标，映射到 three 的 z 轴（y 轴向上）
      this.equipmentList.filter(it => it.layoutX != null).forEach(it => this.createDeviceMesh(it))

      // 事件绑定必须先于 OrbitControls 创建：自己的 pointerdown 先注册先执行，
      // 命中设备时立刻置 controls.enabled=false，OrbitControls 自身 pointerdown 入口的 enabled 检查会直接 return，
      // 从根上避免「拖设备」和「转相机」两种手势冲突
      this.bindCanvasEvents()
      const controls = new OrbitControls(camera, renderer.domElement)
      controls.target.set(0, 0.5, 0)
      controls.enableDamping = true
      controls.maxPolarAngle = (Math.PI / 180) * 85 // 限制仰角，防止视角钻到地面以下
      controls.update()
      this.controls = controls

      this._animateFn = this.animate.bind(this)
      this._animateFn()
    },
    buildWorkshop() {
      const W = 24
      const D = 16
      const H = 2.5
      // 地面
      const ground = new THREE.Mesh(
        new THREE.PlaneGeometry(W, D).rotateX(-Math.PI / 2),
        new THREE.MeshStandardMaterial({ color: 0x141b22, roughness: 0.95 })
      )
      this.scene.add(ground)
      // 网格：GridHelper 只能建正方形，z 向压缩到 16/24 得到 24m×16m 网格；抬高 0.01 防与地面 z-fighting
      const grid = new THREE.GridHelper(W, 24, 0x2a3644, 0x1c2630)
      grid.scale.z = D / W
      grid.position.y = 0.01
      this.scene.add(grid)
      // 四面围墙：半透明玻璃质感 + 亮色描边
      const wallMat = new THREE.MeshStandardMaterial({ color: 0x3a86c8, transparent: true, opacity: 0.08, side: THREE.DoubleSide })
      const edgeMat = new THREE.LineBasicMaterial({ color: 0x4a9eff, transparent: true, opacity: 0.35 })
      const walls = [
        { w: W, x: 0, z: -D / 2, ry: 0 },
        { w: W, x: 0, z: D / 2, ry: 0 },
        { w: D, x: -W / 2, z: 0, ry: Math.PI / 2 },
        { w: D, x: W / 2, z: 0, ry: Math.PI / 2 }
      ]
      walls.forEach(cfg => {
        const geo = new THREE.PlaneGeometry(cfg.w, H)
        const mesh = new THREE.Mesh(geo, wallMat)
        mesh.position.set(cfg.x, H / 2, cfg.z)
        mesh.rotation.y = cfg.ry
        // 描边挂为子对象，随墙一起旋转定位
        mesh.add(new THREE.LineSegments(new THREE.EdgesGeometry(geo), edgeMat))
        this.scene.add(mesh)
      })
    },
    // CanvasTexture 画设备名称标签：深色圆角底 + 白字，深色场景中可读性最好
    makeLabelSprite(text) {
      const canvas = document.createElement('canvas')
      canvas.width = 256
      canvas.height = 64
      const ctx = canvas.getContext('2d')
      ctx.beginPath()
      const r = 12
      ctx.moveTo(r, 0)
      ctx.arcTo(256, 0, 256, 64, r)
      ctx.arcTo(256, 64, 0, 64, r)
      ctx.arcTo(0, 64, 0, 0, r)
      ctx.arcTo(0, 0, 256, 0, r)
      ctx.closePath()
      ctx.fillStyle = 'rgba(13, 19, 26, 0.88)'
      ctx.fill()
      ctx.strokeStyle = 'rgba(64, 158, 255, 0.55)'
      ctx.lineWidth = 2
      ctx.stroke()
      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 24px "Microsoft YaHei", sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      let label = String(text || '')
      // 超长名称截断加省略号，避免相邻设备标签相互遮挡
      if (ctx.measureText(label).width > 232) {
        while (label.length > 1 && ctx.measureText(label + '…').width > 232) {
          label = label.slice(0, -1)
        }
        label += '…'
      }
      ctx.fillText(label, 128, 34)
      const texture = new THREE.CanvasTexture(canvas)
      const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture, depthTest: false }))
      sprite.scale.set(3, 0.75, 1)
      return sprite
    },
    createDeviceMesh(item) {
      const color = new THREE.Color(this.statusColorCss(item.equipmentStatus))
      const group = new THREE.Group()
      // 状态着色 + 同色微自发光，让不同状态在深色场景里也能区分
      const mat = new THREE.MeshStandardMaterial({ color, emissive: color.clone(), emissiveIntensity: 0.25 })
      const box = new THREE.Mesh(new THREE.BoxGeometry(1.2, 1.2, 1.2), mat)
      box.position.y = 0.6
      box.userData.equipmentId = Number(item.id)
      group.add(box)
      const sprite = this.makeLabelSprite(item.equipmentName)
      sprite.position.y = 2.1
      group.add(sprite)
      // 关键坐标映射：layoutX → three.x，layoutY → three.z（three 的 y 轴向上）
      group.position.set(Number(item.layoutX) || 0, 0, Number(item.layoutY) || 0)
      group.userData.equipmentId = Number(item.id)
      this.scene.add(group)
      this.meshMap[Number(item.id)] = group
      this.pickMeshes.push(box)
      return group
    },
    removeDeviceMesh(id) {
      const key = Number(id)
      const group = this.meshMap[key]
      if (!group) return
      const box = group.children.find(c => c.isMesh)
      if (box) {
        const i = this.pickMeshes.indexOf(box)
        if (i > -1) this.pickMeshes.splice(i, 1)
      }
      if (this.selectionHelper && this.selectionHelper.object === group) {
        this.setSelection(null)
      }
      this.scene.remove(group)
      // 反复「移回清单-重新拖入」也不泄漏 GPU 资源
      group.traverse(obj => {
        if (obj.geometry) obj.geometry.dispose()
        if (obj.material) {
          const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
          mats.forEach(m => {
            if (m.map) m.map.dispose()
            m.dispose()
          })
        }
      })
      delete this.meshMap[key]
    },
    // 属性保存成功后刷新 3D 表现：box 状态色 + 重建名称标签
    refreshDeviceMesh(item) {
      const group = this.meshMap[Number(item.id)]
      if (!group) return
      const color = new THREE.Color(this.statusColorCss(item.equipmentStatus))
      const box = group.children.find(c => c.isMesh)
      if (box) {
        box.material.color.copy(color)
        box.material.emissive.copy(color)
      }
      const oldSprite = group.children.find(c => c.isSprite)
      if (oldSprite) {
        group.remove(oldSprite)
        if (oldSprite.material.map) oldSprite.material.map.dispose()
        oldSprite.material.dispose()
      }
      const sprite = this.makeLabelSprite(item.equipmentName)
      sprite.position.y = 2.1
      group.add(sprite)
    },
    // 选中态：BoxHelper 描边高亮（render 循环里持续 update 跟随设备移动）
    setSelection(group) {
      if (this.selectionHelper) {
        this.scene.remove(this.selectionHelper)
        this.selectionHelper.geometry.dispose()
        this.selectionHelper.material.dispose()
        this.selectionHelper = null
      }
      if (group && this.scene) {
        this.selectionHelper = new THREE.BoxHelper(group, 0x409EFF)
        this.scene.add(this.selectionHelper)
      }
    },

    /* ---------- 画布事件 ---------- */
    bindCanvasEvents() {
      const el = this.renderer.domElement
      this._onDown = this.handlePointerDown.bind(this)
      this._onMove = this.handlePointerMove.bind(this)
      this._onUp = this.handlePointerUp.bind(this)
      this._onResize = this.handleResize.bind(this)
      el.addEventListener('pointerdown', this._onDown)
      // move/up 挂 window：拖拽时指针划出画布仍能持续收到事件
      window.addEventListener('pointermove', this._onMove)
      window.addEventListener('pointerup', this._onUp)
      window.addEventListener('resize', this._onResize)
    },
    eventToNdc(e) {
      const rect = this.renderer.domElement.getBoundingClientRect()
      const w = rect.width || 1
      const h = rect.height || 1
      return {
        x: ((e.clientX - rect.left) / w) * 2 - 1,
        y: -((e.clientY - rect.top) / h) * 2 + 1
      }
    },
    raycastDevice(e) {
      this.raycaster.setFromCamera(this.eventToNdc(e), this.camera)
      const hits = this.raycaster.intersectObjects(this.pickMeshes, false)
      return hits.length ? hits[0].object.userData.equipmentId : null
    },
    raycastGround(e) {
      this.raycaster.setFromCamera(this.eventToNdc(e), this.camera)
      const pt = new THREE.Vector3()
      return this.raycaster.ray.intersectPlane(this.groundPlane, pt) ? pt : null
    },
    handlePointerDown(e) {
      if (e.button !== 0 || !this.scene) return // 只响应左键，右键留给视角平移
      this.downPos = { x: e.clientX, y: e.clientY }
      const hitId = this.raycastDevice(e)
      if (hitId !== null && this.meshMap[hitId]) {
        // 按住设备 = 拖拽设备语义，禁用轨道控制（OrbitControls 的 pointerdown 在本监听之后执行，
        // 其入口的 enabled 检查会直接返回，不会开启旋转状态）
        this.dragState = { equipmentId: hitId, group: this.meshMap[hitId], startX: e.clientX, startY: e.clientY, moved: false }
        this.controls.enabled = false
      }
    },
    handlePointerMove(e) {
      if (!this.dragState) return
      const dx = e.clientX - this.dragState.startX
      const dy = e.clientY - this.dragState.startY
      if (!this.dragState.moved) {
        if (Math.sqrt(dx * dx + dy * dy) < CLICK_SLOP_PX) return // 还在点击容差内，不启动拖拽
        this.dragState.moved = true
      }
      const pt = this.raycastGround(e)
      if (!pt) return
      // 射线求地面交点 → 边界钳制 → 可选 0.5m 网格吸附
      let x = THREE.MathUtils.clamp(pt.x, -CLAMP_X, CLAMP_X)
      let z = THREE.MathUtils.clamp(pt.z, -CLAMP_Z, CLAMP_Z)
      if (this.snapOn) {
        x = Math.round(x / SNAP_STEP) * SNAP_STEP
        z = Math.round(z / SNAP_STEP) * SNAP_STEP
      }
      this.dragState.group.position.set(x, 0, z)
      const item = this.findEquipment(this.dragState.equipmentId)
      if (item) {
        item.layoutX = x
        item.layoutY = z
      }
      this.dirty = true
    },
    handlePointerUp(e) {
      if (this.controls) this.controls.enabled = true
      const drag = this.dragState
      this.dragState = null
      if (!this.downPos) return
      const dx = e.clientX - this.downPos.x
      const dy = e.clientY - this.downPos.y
      this.downPos = null
      if (Math.sqrt(dx * dx + dy * dy) >= CLICK_SLOP_PX) return // 位移过大：这是拖拽/旋转，不是点击
      // 点击语义：命中设备→选中该设备；点空白→切回车间模式
      if (drag && drag.equipmentId !== null) {
        this.selectEquipment(drag.equipmentId)
      } else {
        const hitId = this.raycastDevice(e)
        if (hitId !== null) {
          this.selectEquipment(hitId)
        } else {
          this.selectWorkshop()
        }
      }
    },
    handleResize() {
      const container = this.$refs.canvas
      if (!container || !this.renderer) return
      const w = container.clientWidth
      const h = container.clientHeight
      if (!w || !h) return
      this.camera.aspect = w / h
      this.camera.updateProjectionMatrix()
      this.renderer.setSize(w, h)
    },

    /* ---------- 左栏拖放（HTML5 DnD） ---------- */
    onDragStart(e, item) {
      e.dataTransfer.setData('text/plain', String(item.id))
      e.dataTransfer.effectAllowed = 'move'
    },
    handleDrop(e) {
      const id = Number(e.dataTransfer.getData('text/plain'))
      const item = this.findEquipment(id)
      if (!item || !this.scene) return
      const pt = this.raycastGround(e)
      if (!pt) return
      let x = THREE.MathUtils.clamp(pt.x, -CLAMP_X, CLAMP_X)
      let z = THREE.MathUtils.clamp(pt.z, -CLAMP_Z, CLAMP_Z)
      if (this.snapOn) {
        x = Math.round(x / SNAP_STEP) * SNAP_STEP
        z = Math.round(z / SNAP_STEP) * SNAP_STEP
      }
      item.layoutX = x
      item.layoutY = z
      const group = this.meshMap[id]
      if (group) {
        group.position.set(x, 0, z)
      } else {
        this.createDeviceMesh(item)
      }
      this.dirty = true
      this.selectEquipment(id)
    },
    // 双击未摆放设备：自动找一个 0.5m 网格上离所有已摆放设备 ≥1.5m 的空位（仅此处做避让，拖拽不做碰撞检测）
    placeAtFreeSpot(item) {
      if (!this.scene) return
      const placed = this.equipmentList.filter(it => it.layoutX != null)
      const candidates = []
      for (let x = -CLAMP_X; x <= CLAMP_X + 0.001; x += SNAP_STEP) {
        for (let z = -CLAMP_Z; z <= CLAMP_Z + 0.001; z += SNAP_STEP) {
          candidates.push([x, z])
        }
      }
      candidates.sort((a, b) => Math.hypot(a[0], a[1]) - Math.hypot(b[0], b[1]))
      const spot = candidates.find(p => placed.every(it => Math.hypot(it.layoutX - p[0], it.layoutY - p[1]) >= 1.5)) || [0, 0]
      item.layoutX = spot[0]
      item.layoutY = spot[1]
      if (!this.meshMap[Number(item.id)]) {
        this.createDeviceMesh(item)
      }
      this.dirty = true
      this.selectEquipment(item.id)
    },

    /* ---------- 保存 ---------- */
    saveLayout() {
      if (!this.workshopId) return
      this.saving = true
      // null = 移回清单；布局保存只提交 id + 坐标，与设备属性接口天然隔离
      const data = this.equipmentList.map(it => ({
        id: it.id,
        layoutX: it.layoutX == null ? null : it.layoutX,
        layoutY: it.layoutY == null ? null : it.layoutY
      }))
      saveWorkshopLayout(this.workshopId, data).then(() => {
        this.dirty = false
        this.$modal.msgSuccess('布局已保存')
      }).finally(() => {
        this.saving = false
      })
    },
    saveDevice() {
      this.$refs.deviceFormRef.validate(valid => {
        if (!valid) return
        const f = this.deviceForm
        this.savingProp = true
        // 不携带 layout 字段：后端设备 DTO 无此字段，布局只走 saveLayout 接口
        updateEquipment({
          id: f.id,
          equipmentNo: f.equipmentNo,
          equipmentName: f.equipmentName,
          equipmentModelName: f.equipmentModelName,
          equipmentStatus: f.equipmentStatus,
          equipmentRemark: f.equipmentRemark,
          workshopId: this.workshop.id,
          workshopName: this.workshop.workshopName
        }).then(() => {
          this.$modal.msgSuccess('设备属性已保存')
          const item = this.findEquipment(f.id)
          if (item) {
            item.equipmentName = f.equipmentName
            item.equipmentModelName = f.equipmentModelName
            item.equipmentStatus = f.equipmentStatus
            item.equipmentRemark = f.equipmentRemark
            this.refreshDeviceMesh(item)
          }
        }).finally(() => {
          this.savingProp = false
        })
      })
    },
    moveBackToList() {
      const item = this.selectedEquipment
      if (!item || item.layoutX == null) return
      this.$modal.confirm(`确认将设备“${item.equipmentName}”移回清单？`).then(() => {
        item.layoutX = null
        item.layoutY = null
        this.removeDeviceMesh(item.id)
        this.dirty = true
        this.selectWorkshop()
      }).catch(() => {})
    },
    saveWorkshopProp() {
      this.$refs.workshopFormRef.validate(valid => {
        if (!valid) return
        this.savingProp = true
        // 完整字段含 id 提交，成功后同步本地 workshop（顶栏标题随之刷新）
        updateWorkshop({ ...this.workshopForm }).then(() => {
          Object.assign(this.workshop, this.workshopForm)
          this.$modal.msgSuccess('车间属性已保存')
        }).finally(() => {
          this.savingProp = false
        })
      })
    },

    /* ---------- 渲染循环与资源释放 ---------- */
    animate() {
      this.rafId = requestAnimationFrame(this._animateFn)
      if (this.controls) this.controls.update() // enableDamping 需要逐帧更新
      if (this.selectionHelper) this.selectionHelper.update() // 选中框跟随设备移动
      if (this.renderer && this.scene && this.camera) {
        this.renderer.render(this.scene, this.camera)
      }
    },
    destroyThree() {
      if (this.rafId) {
        cancelAnimationFrame(this.rafId)
        this.rafId = null
      }
      if (this._onMove) window.removeEventListener('pointermove', this._onMove)
      if (this._onUp) window.removeEventListener('pointerup', this._onUp)
      if (this._onResize) window.removeEventListener('resize', this._onResize)
      if (this.renderer && this._onDown) {
        this.renderer.domElement.removeEventListener('pointerdown', this._onDown)
      }
      if (this.controls) {
        this.controls.dispose()
        this.controls = null
      }
      if (this.scene) {
        // 递归释放 GPU 侧资源：geometry / material / 贴图（dispose 幂等，共享材质重复释放无副作用）
        this.scene.traverse(obj => {
          if (obj.geometry) obj.geometry.dispose()
          if (obj.material) {
            const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
            mats.forEach(m => {
              if (m.map) m.map.dispose()
              m.dispose()
            })
          }
        })
        this.scene = null
      }
      if (this.renderer) {
        const el = this.renderer.domElement
        if (el && el.parentNode) el.parentNode.removeChild(el)
        this.renderer.dispose()
        this.renderer = null
      }
      this.camera = null
      this.meshMap = {}
      this.pickMeshes = []
      this.dragState = null
      this.selectionHelper = null
      this._animateFn = null
    }
  }
}
</script>

<style lang="scss" scoped>
// 页面高度：视口高 - 顶栏/标签页 84px - 底部版权 36px，正好占满内容区且不出滚动条
.twin-page {
  height: calc(100vh - 120px);
  min-height: 480px;
  display: flex;
  flex-direction: column;
  background: #0b0f14;
  overflow: hidden;
}

.twin-header {
  flex: none;
  height: 48px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  background: #0e141b;
  border-bottom: 1px solid #1c2630;

  .twin-title {
    flex: 1;
    min-width: 0;
    margin: 0 12px;
    font-size: 15px;
    font-weight: 600;
    color: #dbe4ee;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .twin-actions {
    display: flex;
    align-items: center;
    flex: none;

    .snap-text {
      margin-right: 8px;
      font-size: 13px;
      color: #8a9bb0;
    }

    .el-switch {
      margin-right: 16px;
    }

    // dirty 红点：直观提示存在未保存修改
    .dirty-dot {
      display: inline-block;
      width: 6px;
      height: 6px;
      margin-left: 4px;
      vertical-align: middle;
      border-radius: 50%;
      background: #f56c6c;
    }
  }
}

.twin-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.twin-left {
  flex: none;
  width: 220px;
  padding: 10px;
  background: #0e141b;
  border-right: 1px solid #1c2630;
  overflow-y: auto;
}

.twin-canvas {
  position: relative;
  flex: 1;
  min-width: 0;

  .canvas-hint {
    position: absolute;
    left: 12px;
    bottom: 10px;
    z-index: 2;
    padding: 4px 10px;
    font-size: 12px;
    color: #5c7189;
    background: rgba(11, 15, 20, 0.7);
    border: 1px solid #1c2630;
    border-radius: 3px;
    pointer-events: none; // 提示条不挡画布交互
  }
}

.twin-right {
  flex: none;
  width: 280px;
  padding: 10px;
  background: #0e141b;
  border-left: 1px solid #1c2630;
  overflow-y: auto;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #dbe4ee;
  margin-bottom: 8px;
}

.panel-sub {
  margin: 10px 0 6px;
  font-size: 12px;
  color: #8a9bb0;
}

.panel-tip {
  margin-bottom: 10px;
  font-size: 12px;
  color: #5c7189;
  line-height: 1.5;
}

.device-item {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
  padding: 6px 8px;
  font-size: 12px;
  color: #c3cedb;
  background: #131b24;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;

  &:hover {
    border-color: #2a4a6e;
  }

  &.active {
    border-color: #409EFF;
    background: #142033;
  }

  &.unplaced {
    cursor: grab; // 可拖入画布
  }

  .status-dot {
    flex: none;
    width: 8px;
    height: 8px;
    margin-right: 6px;
    border-radius: 50%;
  }

  .device-no {
    flex: none;
    margin-right: 6px;
    color: #8a9bb0;
  }

  .device-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.empty-tip {
  padding: 8px 4px;
  font-size: 12px;
  color: #4a5a6d;
}

.form-btns {
  margin-top: 4px;
  padding-left: 80px;
}

.coord-text {
  font-size: 13px;
  color: #8a9bb0;
}

// Element UI 表单深色适配（局部覆盖，不影响其他页面）
.twin-right ::v-deep {
  .el-form-item__label {
    color: #8a9bb0;
  }

  .el-input__inner,
  .el-textarea__inner {
    background-color: #10161d;
    border-color: #2a3644;
    color: #dbe4ee;
  }

  .el-input__inner::placeholder,
  .el-textarea__inner::placeholder {
    color: #4a5a6d;
  }

  .el-input.is-disabled .el-input__inner {
    background-color: #0d1218;
    border-color: #1c2630;
    color: #5c6b7d;
  }

  .el-form-item__error {
    color: #f1939c;
  }
}
</style>
