import Layout from '@/layout'

export const businessRoutes = [
  {
    path: '/machine-business',
    component: Layout,
    redirect: '/machine-business/device',
    name: 'MachineBusiness',
    alwaysShow: true,
    meta: { title: '智能制造', icon: 'dashboard' },
    children: [
      {
        path: 'workshop',
        component: () => import('@/views/machine/workshop/index'),
        name: 'BusinessWorkshop',
        meta: { title: '车间管理', icon: 'tree' }
      },
      {
        // 车间管理「数字孪生」入口页：hidden 不显示在侧栏；noCache 离开即销毁并释放 3D 资源
        path: 'workshop/twin',
        component: () => import('@/views/machine/workshop/twin'),
        name: 'BusinessWorkshopTwin',
        meta: { title: '车间孪生编辑', noCache: true },
        hidden: true
      },
      {
        path: 'device',
        component: () => import('@/views/machine/device/list'),
        name: 'BusinessDevice',
        meta: { title: '设备台账', icon: 'server' }
      },
      {
        path: 'sensor',
        component: () => import('@/views/machine/device/sensor-manage'),
        name: 'BusinessSensor',
        meta: { title: '传感器管理', icon: 'monitor' }
      },
      {
        // 设备台账「实时监测」入口页：hidden 不显示在侧栏菜单；noCache 离开即销毁并断开数据流
        path: 'device/sensor-monitor',
        component: () => import('@/views/machine/device/sensor-monitor'),
        name: 'BusinessSensorMonitor',
        meta: { title: '传感器实时监测', icon: 'monitor', noCache: true },
        hidden: true
      },
      // {
      //   path: 'inspection',
      //   component: () => import('@/views/machine/inspection'),
      //   name: 'BusinessInspection',
      //   meta: { title: '零件检测', icon: 'validCode' }
      // },
      // {
      //   path: 'ai-model',
      //   component: () => import('@/views/machine/ai-model/config'),
      //   name: 'BusinessAiModel',
      //   meta: { title: 'AI模型配置', icon: 'example' }
      // },
      // {
      //   path: 'ai-service',
      //   component: () => import('@/views/machine/ai-service'),
      //   name: 'BusinessAiService',
      //   meta: { title: 'AI智能分析', icon: 'monitor' }
      // },
      {
        path: 'alert-rule',
        component: () => import('@/views/machine/alert/rule'),
        name: 'BusinessAlertRule',
        meta: { title: '告警规则', icon: 'bug' }
      },
      {
        path: 'warning',
        component: () => import('@/views/machine/alert/warning'),
        name: 'BusinessWarning',
        meta: { title: '故障预警', icon: 'message', businessType: 'warning' }
      },

      {
        path: 'work-order',
        component: () => import('@/views/machine/operations'),
        name: 'BusinessWorkOrder',
        meta: { title: '维保工单', icon: 'form', businessType: 'order' }
      },
      {
        path: 'maintenance-plan',
        component: () => import('@/views/machine/operations'),
        name: 'BusinessMaintenancePlan',
        meta: { title: '维护计划', icon: 'date', businessType: 'plan' }
      },
      {
        path: 'report',
        component: () => import('@/views/machine/report/overview'),
        name: 'BusinessReport',
        meta: { title: '统计报表', icon: 'chart' }
      },
      {
        path: 'inspection-log',
        component: () => import('@/views/machine/operations'),
        name: 'BusinessInspectionLog',
        meta: { title: '检测日志', icon: 'log', businessType: 'inspectionLog' }
      },
      {
        path: 'device-log',
        component: () => import('@/views/machine/operations'),
        name: 'BusinessDeviceLog',
        meta: { title: '设备日志', icon: 'log', businessType: 'deviceLog' }
      }
    ]
  }
]
