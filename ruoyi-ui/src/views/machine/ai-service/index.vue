<template>
  <div class="app-container">
    <el-row :gutter="16">
      <el-col :span="8"><el-card shadow="never"><div slot="header">服务状态</div><el-result :icon="healthOk ? 'success' : 'warning'" :title="healthOk ? 'AI 服务正常' : 'AI 服务未连接'" :sub-title="healthMessage"><template slot="extra"><el-button type="primary" :loading="healthLoading" @click="checkHealth">重新检测</el-button></template></el-result></el-card></el-col>
      <el-col :span="16"><el-card shadow="never"><div slot="header">预测性维护</div><el-form :inline="true" size="small"><el-form-item label="设备ID"><el-input-number v-model="deviceId" :min="1" /></el-form-item><el-form-item label="预测时长"><el-input-number v-model="hours" :min="1" :max="1000" /></el-form-item><el-form-item><el-button type="primary" :loading="predictLoading" @click="runPredict">执行预测</el-button><el-button @click="runAllPredict">预测全部设备</el-button></el-form-item></el-form><pre class="result-panel">{{ formattedPredictResult }}</pre></el-card></el-col>
    </el-row>
    <el-row :gutter="16" class="mt16">
      <el-col :span="12"><el-card shadow="never"><div slot="header">边缘推理</div><el-form label-width="80px" size="small"><el-form-item label="节点ID"><el-input v-model="edgeForm.nodeId" /></el-form-item><el-form-item label="振动"><el-input-number v-model="edgeForm.vibration" :precision="2" /></el-form-item><el-form-item label="温度"><el-input-number v-model="edgeForm.temperature" :precision="2" /></el-form-item><el-form-item label="电流"><el-input-number v-model="edgeForm.current" :precision="2" /></el-form-item><el-form-item><el-button type="primary" :loading="edgeLoading" @click="runEdge">开始推理</el-button></el-form-item></el-form><pre class="result-panel">{{ formattedEdgeResult }}</pre></el-card></el-col>
      <el-col :span="12"><el-card shadow="never"><div slot="header">知识图谱抽取</div><el-input v-model="knowledgeText" type="textarea" :rows="8" placeholder="输入设备故障、检测或维修文本" /><el-button type="primary" class="mt12" :loading="knowledgeLoading" @click="runKnowledge">抽取实体关系</el-button><pre class="result-panel">{{ formattedKnowledgeResult }}</pre></el-card></el-col>
    </el-row>
  </div>
</template>
<script>
import { checkAiHealth, predictRul, predictAllDevices, runEdgeInference, extractKnowledge } from '@/api/business/ai'
export default {
  name: 'BusinessAiService',
  data() { return { healthOk: false, healthLoading: false, healthMessage: '尚未检测', deviceId: 1, hours: 100, predictLoading: false, predictResult: null, edgeLoading: false, edgeForm: { nodeId: 'edge-01', vibration: 2.4, temperature: 46, current: 8.2 }, edgeResult: null, knowledgeText: '', knowledgeLoading: false, knowledgeResult: null } },
  computed: { formattedPredictResult() { return this.format(this.predictResult) }, formattedEdgeResult() { return this.format(this.edgeResult) }, formattedKnowledgeResult() { return this.format(this.knowledgeResult) } },
  created() { this.checkHealth() },
  methods: {
    format(data) { return data ? JSON.stringify(data, null, 2) : '暂无结果' },
    checkHealth() { this.healthLoading = true; checkAiHealth().then(res => { this.healthOk = true; this.healthMessage = typeof res === 'string' ? res : '后端 AI 服务已响应' }).catch(err => { this.healthOk = false; this.healthMessage = err.message || '连接失败' }).finally(() => { this.healthLoading = false }) },
    runPredict() { this.predictLoading = true; predictRul(this.deviceId, this.hours).then(res => { this.predictResult = res }).finally(() => { this.predictLoading = false }) },
    runAllPredict() { this.predictLoading = true; predictAllDevices().then(res => { this.predictResult = res }).finally(() => { this.predictLoading = false }) },
    runEdge() { this.edgeLoading = true; runEdgeInference(this.edgeForm).then(res => { this.edgeResult = res }).finally(() => { this.edgeLoading = false }) },
    runKnowledge() { if (!this.knowledgeText.trim()) return this.$modal.msgWarning('请输入待抽取文本'); this.knowledgeLoading = true; extractKnowledge({ text: this.knowledgeText }).then(res => { this.knowledgeResult = res }).finally(() => { this.knowledgeLoading = false }) }
  }
}
</script>
<style scoped>.mt12{margin-top:12px}.mt16{margin-top:16px}.result-panel{min-height:90px;max-height:280px;overflow:auto;margin-top:12px;padding:12px;background:#f7f8fa;border:1px solid #ebeef5;white-space:pre-wrap;word-break:break-all}</style>
