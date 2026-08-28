export const faultWarnings = [
  { id: 1, warningNo: 'FW-202608-001', machineName: 'CNC加工中心-01', warningType: '主轴振动异常', level: '紧急', warningTime: '2026-08-28 08:20:00', predictedFaultTime: '2026-08-29 10:00:00', handleStatus: '未处理', handler: '' },
  { id: 2, warningNo: 'FW-202608-002', machineName: '数控车床-02', warningType: '温度持续升高', level: '重要', warningTime: '2026-08-28 07:45:00', predictedFaultTime: '2026-08-31 12:00:00', handleStatus: '处理中', handler: '李工' },
  { id: 3, warningNo: 'FW-202608-003', machineName: '五轴联动加工中心', warningType: '电流波动', level: '一般', warningTime: '2026-08-27 16:30:00', predictedFaultTime: '2026-09-03 09:00:00', handleStatus: '已处理', handler: '王工' }
]
export const workOrders = [
  { id: 1, orderNo: 'WO-202608-001', machineName: 'CNC加工中心-01', orderType: '故障维修', content: '检查主轴轴承与振动传感器', assignee: '张工', status: '待处理', createTime: '2026-08-28 08:35:00' },
  { id: 2, orderNo: 'WO-202608-002', machineName: '数控车床-02', orderType: '预防维护', content: '检查冷却系统并更换滤芯', assignee: '李工', status: '处理中', createTime: '2026-08-27 14:10:00' },
  { id: 3, orderNo: 'WO-202608-003', machineName: '数控磨床-01', orderType: '巡检', content: '月度精度与润滑检查', assignee: '赵工', status: '已完成', createTime: '2026-08-26 09:20:00' }
]
export const maintenancePlans = [
  { id: 1, planNo: 'MP-202608-001', machineName: 'CNC加工中心-01', maintenanceType: '一级保养', planDate: '2026-08-30', owner: '张工', execStatus: '待执行' },
  { id: 2, planNo: 'MP-202608-002', machineName: '五轴联动加工中心', maintenanceType: '精度校准', planDate: '2026-09-02', owner: '王工', execStatus: '待执行' },
  { id: 3, planNo: 'MP-202608-003', machineName: '数控车床-02', maintenanceType: '预防维护', planDate: '2026-08-27', owner: '李工', execStatus: '已完成' }
]
export const inspectionLogs = [
  { id: 1, logTime: '2026-08-28 08:40:12', partCode: 'PART-202608-101', partName: '传动轴', action: 'AI智能检测', operator: 'admin', status: '成功', detail: '检测完成，判定合格' },
  { id: 2, logTime: '2026-08-28 08:22:05', partCode: 'PART-202608-100', partName: '法兰盘', action: 'AI智能检测', operator: 'admin', status: '异常', detail: '未找到对应检测标准' }
]
export const deviceLogs = [
  { id: 1, logTime: '2026-08-28 08:30:00', deviceCode: 'MC-001', deviceName: 'CNC加工中心-01', logType: '告警触发', logContent: '主轴振动超过预警阈值', operator: '系统' },
  { id: 2, logTime: '2026-08-28 07:55:00', deviceCode: 'MC-002', deviceName: 'CNC加工中心-02', logType: '状态变更', logContent: '设备由待机切换为运行', operator: '张工' }
]
