<template>
  <div class="app-container">
    <el-alert title="报表数据沿用 ht_admin 演示口径，目前为前端本地统计数据。" type="info" :closable="false" show-icon class="mb16" />
    <el-row :gutter="16"><el-col v-for="item in metrics" :key="item.label" :span="4"><el-card shadow="hover" class="metric-card"><div class="metric-label">{{ item.label }}</div><div class="metric-value" :style="{color:item.color}">{{ item.value }}</div></el-card></el-col></el-row>
    <el-row :gutter="16" class="mt16"><el-col :span="12"><el-card shadow="never"><div slot="header">检测量与良品率趋势</div><div ref="trend" class="chart" /></el-card></el-col><el-col :span="12"><el-card shadow="never"><div slot="header">缺陷类型分布</div><div ref="defect" class="chart" /></el-card></el-col></el-row>
    <el-row :gutter="16" class="mt16"><el-col :span="12"><el-card shadow="never"><div slot="header">设备运行状态</div><div ref="device" class="chart" /></el-card></el-col><el-col :span="12"><el-card shadow="never"><div slot="header">维护工单统计</div><div ref="maintenance" class="chart" /></el-card></el-col></el-row>
  </div>
</template>
<script>
import * as echarts from 'echarts'
export default {
  name: 'BusinessReportOverview',
  data() { return { charts: [], metrics: [{ label: '检测总量', value: '12,680件', color: '#409EFF' }, { label: '综合良品率', value: '97.6%', color: '#67C23A' }, { label: '不良品数', value: '304件', color: '#F56C6C' }, { label: '在线设备', value: '18/22', color: '#409EFF' }, { label: '待处理告警', value: '6条', color: '#E6A23C' }, { label: '设备稼动率', value: '86.4%', color: '#67C23A' }] } },
  mounted() { this.renderCharts(); window.addEventListener('resize', this.resizeCharts) }, beforeDestroy() { window.removeEventListener('resize', this.resizeCharts); this.charts.forEach(c => c.dispose()) },
  methods: {
    create(ref, option) { const chart = echarts.init(this.$refs[ref]); chart.setOption(option); this.charts.push(chart) }, resizeCharts() { this.charts.forEach(c => c.resize()) },
    renderCharts() {
      const common = { tooltip: { trigger: 'axis' }, grid: { left: 45, right: 25, top: 35, bottom: 35 } }
      this.create('trend', { ...common, legend: { data: ['检测量', '良品率'] }, xAxis: { type: 'category', data: ['8/22','8/23','8/24','8/25','8/26','8/27','8/28'] }, yAxis: [{ type: 'value' }, { type: 'value', min: 90, max: 100 }], series: [{ name: '检测量', type: 'bar', data: [1320,1480,1650,1510,1780,1940,2100] }, { name: '良品率', type: 'line', yAxisIndex: 1, smooth: true, data: [96.2,97.1,96.8,97.5,98.0,97.4,97.8] }] })
      this.create('defect', { tooltip: { trigger: 'item' }, legend: { orient: 'vertical', left: 10 }, series: [{ type: 'pie', radius: ['38%','68%'], data: [{name:'尺寸超差',value:112},{name:'表面划痕',value:78},{name:'毛刺',value:54},{name:'形变',value:36},{name:'裂纹',value:24}] }] })
      this.create('device', { ...common, xAxis: { type: 'category', data: ['运行中','待机','维护中','离线'] }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: [{value:12,itemStyle:{color:'#67C23A'}},{value:6,itemStyle:{color:'#409EFF'}},{value:3,itemStyle:{color:'#E6A23C'}},{value:1,itemStyle:{color:'#909399'}}] }] })
      this.create('maintenance', { tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: '65%', data: [{name:'待处理',value:5},{name:'处理中',value:3},{name:'已完成',value:18}] }] })
    }
  }
}
</script>
<style scoped>.mb16{margin-bottom:16px}.mt16{margin-top:16px}.metric-card{text-align:center}.metric-label{color:#909399;font-size:13px}.metric-value{margin-top:10px;font-size:24px;font-weight:600}.chart{height:320px}</style>
