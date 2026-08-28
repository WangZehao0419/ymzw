<template>
  <div v-loading="loading" element-loading-text="正在加载监测数据" class="app-container sensor-monitor">
    <!-- 顶部信息条：返回 / 设备名 / 设备切换 / 连接状态 -->
    <div class="monitor-header">
      <el-button size="small" icon="el-icon-back" @click="goBack">返回</el-button>
      <el-tag effect="dark" type="info" class="device-tag">{{ currentEquipmentName }}</el-tag>
      <el-select
        v-model="equipmentId"
        size="small"
        filterable
        clearable
        placeholder="切换设备"
        class="device-select"
        @change="handleEquipmentChange"
      >
        <el-option v-for="item in equipmentList" :key="item.id" :label="item.equipmentName" :value="item.id" />
      </el-select>
      <el-tag :type="statusTag.type" effect="dark" class="status-tag">
        <i :class="statusTag.type === 'success' ? 'el-icon-loading' : 'el-icon-warning-outline'" />
        {{ statusTag.text }}
      </el-tag>
    </div>

    <template v-if="sensors.length">
      <!-- 传感器实时数值卡片区 -->
      <el-row :gutter="16" class="value-area">
        <el-col v-for="s in sensors" :key="'card-' + s.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card shadow="hover" class="value-card">
            <div class="value-card-name" :title="s.sensorName">{{ s.sensorName }}</div>
            <div class="value-card-value">
              <span class="num">{{ s.value === null || s.value === undefined ? '--' : s.value }}</span>
              <span class="unit">{{ s.sensorUnit || '' }}</span>
            </div>
            <div class="value-card-code">{{ s.sensorCode }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 传感器实时曲线区：每个传感器一张图 -->
      <el-row :gutter="16" class="chart-area">
        <el-col v-for="s in sensors" :key="'chart-' + s.id" :xs="24" :md="12">
          <el-card shadow="never" class="chart-card">
            <div slot="header" class="chart-card-header">
              <span>{{ s.sensorName }}</span>
              <span class="chart-card-unit">{{ s.sensorUnit || '' }}</span>
            </div>
            <div :ref="'chart_' + s.id" class="chart-box" />
          </el-card>
        </el-col>
      </el-row>
    </template>

    <el-empty v-else-if="!loading" :description="emptyText" />
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { listEquipment, listSensor, fetchMonitorHistory } from '@/api/business/machine'
import { subscribe } from '@/utils/ndjsonStream'

// 曲线滚动窗口大小：新数据入队，超过该长度则淘汰最旧数据
const MAX_POINTS = 60

export default {
  name: 'BusinessSensorMonitor',
  data() {
    return {
      // 网关前缀（开发环境为 /dev-api，由 devServer 代理转发）
      baseApi: process.env.VUE_APP_BASE_API,
      loading: false,
      // 当前设备 ID（Number）
      equipmentId: undefined,
      equipmentList: [],
      // 传感器状态：[{ id, sensorCode, sensorName, sensorUnit, value, times[], values[] }]
      sensors: [],
      // 流连接状态：connecting（本地初始态）| connected | reconnecting | closed
      status: 'connecting',
      // 流退订函数（null 表示当前未订阅）
      unsubscribe: null,
      // sensorId -> echarts 实例
      charts: {},
      // 设备切换序号：快速连续切换时丢弃过期异步响应，防止旧设备数据覆盖新设备
      loadSeq: 0
    }
  },
  computed: {
    currentEquipmentName() {
      const eq = this.equipmentList.find(e => Number(e.id) === Number(this.equipmentId))
      return eq ? eq.equipmentName : (this.equipmentId ? '设备 #' + this.equipmentId : '未选择设备')
    },
    statusTag() {
      if (this.status === 'connected') {
        return { type: 'success', text: '实时监控中' }
      }
      if (this.status === 'connecting') {
        return { type: 'warning', text: '连接建立中' }
      }
      return { type: 'danger', text: '连接中断' }
    },
    emptyText() {
      return this.equipmentList.length ? '该设备暂无传感器' : '暂无设备'
    }
  },
  watch: {
    // 组件被同路由复用（仅 query 变化，如浏览器前进后退）时 mounted 不会再触发，统一由这里切换
    '$route.query.equipmentId'(val) {
      if (val && Number(val) !== Number(this.equipmentId)) {
        this.switchEquipment(Number(val))
      }
    }
  },
  mounted() {
    window.addEventListener('resize', this.handleResize)
    this.loading = true
    listEquipment({ pageNum: 1, pageSize: 100 }).then(res => {
      this.equipmentList = res.rows || []
      const queryId = Number(this.$route.query.equipmentId)
      if (queryId) {
        this.switchEquipment(queryId)
      } else if (this.equipmentList.length) {
        // 未指定设备时默认选第一台：replace 更新 query 由 watch 完成加载
        this.$router.replace({ query: { equipmentId: this.equipmentList[0].id } })
      } else {
        this.loading = false
      }
    }).catch(() => {
      this.loading = false
    })
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize)
    // 释放流订阅与图表实例
    this.teardown()
  },
  methods: {
    // 释放当前设备相关的流连接与图表资源
    teardown() {
      if (this.unsubscribe) {
        this.unsubscribe()
        this.unsubscribe = null
      }
      Object.keys(this.charts).forEach(key => {
        if (this.charts[key]) {
          this.charts[key].dispose()
        }
      })
      this.charts = {}
      this.sensors = []
      this.status = 'connecting'
    },
    // 切换设备：断旧连接 → 拉传感器 → 初始化图表 → 预加载历史 → 订阅实时流
    switchEquipment(id) {
      this.loadSeq++
      const seq = this.loadSeq
      this.teardown()
      this.equipmentId = id
      if (!id) {
        this.loading = false
        return
      }
      this.loading = true
      listSensor({ equipmentId: id, pageNum: 1, pageSize: 100 }).then(res => {
        if (seq !== this.loadSeq) {
          return // 已切换到其他设备，丢弃过期响应
        }
        this.sensors = (res.rows || []).map(s => ({
          id: s.id,
          sensorCode: s.sensorCode,
          sensorName: s.sensorName,
          sensorUnit: s.sensorUnit,
          value: null,
          times: [],
          values: []
        }))
        return this.$nextTick()
      }).then(() => {
        if (seq !== this.loadSeq) {
          return
        }
        // v-for 动态渲染后 ref 才可用，此处初始化空图表
        this.initCharts()
        // 先预加载历史（倒序取最新 60 条），完成后再订阅流，避免流数据与历史数据乱序
        return Promise.all(this.sensors.map(s => this.preloadHistory(s)))
      }).then(() => {
        if (seq !== this.loadSeq) {
          return
        }
        this.loading = false
        this.subscribeStream()
      }).catch(() => {
        if (seq === this.loadSeq) {
          this.loading = false
        }
      })
    },
    // 初始化各传感器图表实例（v-for 中的 ref 是数组）
    initCharts() {
      this.$nextTick(() => {
        this.sensors.forEach(s => {
          const ref = this.$refs['chart_' + s.id]
          const el = Array.isArray(ref) ? ref[0] : ref
          if (!el) {
            return
          }
          this.charts[s.id] = echarts.init(el)
          this.applyChartOption(s)
        })
      })
    },
    // 预加载单个传感器历史数据：后端按时间倒序返回，反转为正序后填入图表
    preloadHistory(s) {
      return fetchMonitorHistory({ sensorId: s.id, page: 1, pageSize: MAX_POINTS }).then(res => {
        const rows = (res.rows || []).slice().reverse()
        rows.forEach(vo => this.appendPoint(s, vo))
        this.applyChartOption(s)
      }).catch(() => {
        // 历史加载失败不阻塞实时订阅
      })
    },
    // 追加一个数据点并维护 60 点滚动窗口
    appendPoint(s, vo) {
      if (vo.sensorValue === null || vo.sensorValue === undefined) {
        return
      }
      s.times.push(this.formatTime(vo.createTime))
      s.values.push(vo.sensorValue)
      if (s.times.length > MAX_POINTS) {
        s.times.shift()
        s.values.shift()
      }
      s.value = vo.sensorValue
    },
    // 将传感器当前数据渲染到对应图表（notMerge 保证切换设备后不残留旧配置）
    applyChartOption(s) {
      const chart = this.charts[s.id]
      if (!chart) {
        return
      }
      chart.setOption({
        grid: { left: 10, right: 15, top: 30, bottom: 20, containLabel: true },
        tooltip: {
          trigger: 'axis',
          formatter: params => {
            const p = params[0]
            return p.name + '<br/>' + s.sensorName + '：' + p.value + ' ' + (s.sensorUnit || '')
          }
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: s.times,
          axisTick: { show: false }
        },
        // scale: 数值集中在小范围时 y 轴不从 0 开始，曲线更易读
        yAxis: { type: 'value', scale: true },
        series: [{
          name: s.sensorName,
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: s.values,
          areaStyle: { opacity: 0.08 }
        }],
        // 高频实时刷新关闭动画，避免抖动
        animation: false
      }, true)
    },
    // 订阅当前设备的实时数据流
    subscribeStream() {
      if (!this.equipmentId) {
        return
      }
      const url = this.baseApi + '/api/equipment/monitor/stream/' + this.equipmentId
      this.unsubscribe = subscribe(url, {
        onMessage: vo => this.handleStreamMessage(vo),
        onStatus: st => { this.status = st }
      })
    },
    // 流消息：更新对应传感器的卡片数值与曲线
    handleStreamMessage(vo) {
      const s = this.sensors.find(x => Number(x.id) === Number(vo.sensorId))
      if (!s) {
        return // 传感器列表里没有（如列表未刷新），忽略
      }
      this.appendPoint(s, vo)
      this.applyChartOption(s)
    },
    // 下拉切换设备：直接切换并同步路由 query（watch 判重后不会重复加载）
    handleEquipmentChange(id) {
      if (!id) {
        return
      }
      this.switchEquipment(id)
      this.$router.replace({ query: { ...this.$route.query, equipmentId: id } })
    },
    handleResize() {
      Object.keys(this.charts).forEach(key => {
        if (this.charts[key]) {
          this.charts[key].resize()
        }
      })
    },
    goBack() {
      this.$router.push('/machine-business/device')
    },
    // 提取 HH:mm:ss：兼容 ISO 格式（2026-08-28T12:34:56）、空格分隔、数组三种序列化形式
    formatTime(t) {
      if (t === null || t === undefined) {
        return ''
      }
      if (Array.isArray(t) && t.length >= 3) {
        const pad = n => (n < 10 ? '0' + n : '' + n)
        return pad(t[t.length - 3] || 0) + ':' + pad(t[t.length - 2] || 0) + ':' + pad(t[t.length - 1] || 0)
      }
      const m = String(t).match(/(\d{1,2}:\d{2}:\d{2})/)
      return m ? m[1] : String(t)
    }
  }
}
</script>

<style lang="scss" scoped>
.monitor-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;

  .device-tag {
    margin-left: 12px;
    max-width: 260px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .device-select {
    margin-left: 12px;
    width: 240px;
  }

  .status-tag {
    margin-left: auto;

    i {
      margin-right: 4px;
    }
  }
}

.value-area {
  margin-bottom: 8px;
}

.value-card {
  margin-bottom: 16px;
  text-align: center;

  .value-card-name {
    font-size: 14px;
    color: #606266;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .value-card-value {
    padding: 8px 0;

    .num {
      font-size: 30px;
      font-weight: 700;
      color: #303133;
    }

    .unit {
      margin-left: 4px;
      font-size: 13px;
      color: #909399;
    }
  }

  .value-card-code {
    font-size: 12px;
    color: #c0c4cc;
  }
}

.chart-area {
  .chart-card {
    margin-bottom: 16px;

    .chart-card-header {
      display: flex;
      justify-content: space-between;
      font-size: 14px;

      .chart-card-unit {
        color: #909399;
        font-size: 12px;
      }
    }

    .chart-box {
      width: 100%;
      height: 260px;
    }
  }
}
</style>
