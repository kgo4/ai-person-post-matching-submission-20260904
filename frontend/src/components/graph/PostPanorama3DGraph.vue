<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as THREE from 'three'
import type { PanoramaGraphData } from '@/api/post-panorama'
import {
  buildPostPanorama3DGraph,
  type Panorama3DGraph,
  type Panorama3DLayoutMode,
  type Panorama3DNode,
  shouldShowPanoramaNodeLabel,
} from './postPanorama3d'

const props = withDefaults(defineProps<{
  graphData: PanoramaGraphData | null
  layoutMode?: Panorama3DLayoutMode
  focusNodeId?: string
}>(), {
  layoutMode: 'stack',
})

const emit = defineEmits<{
  (e: 'node-click', node: Panorama3DNode): void
}>()

const containerRef = ref<HTMLDivElement>()
const tooltip = ref({ show: false, x: 0, y: 0, label: '', meta: '' })
const selectedId = ref('')

const graph = computed<Panorama3DGraph>(() => {
  if (!props.graphData) return { nodes: [], edges: [], centerNode: null }
  return buildPostPanorama3DGraph(props.graphData, { layoutMode: props.layoutMode, focusNodeId: props.focusNodeId })
})

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let frameId = 0
let nodeGroup = new THREE.Group()
let edgeGroup = new THREE.Group()
let edgeFlowGroup = new THREE.Group()
let shellStarParticles: THREE.Points | null = null
let shellGroup = new THREE.Group()
let raycaster = new THREE.Raycaster()
let pointer = new THREE.Vector2()
let nodeMeshes: THREE.Mesh[] = []
let edgeLines: THREE.Line[] = []
let flowParticles: Array<{ points: THREE.Points; positions: Float32Array; speeds: Float32Array; source: THREE.Mesh; target: THREE.Mesh }> = []
let physicsNodes: Array<{
  mesh: THREE.Mesh
  node: Panorama3DNode
  base: THREE.Vector3
  velocity: THREE.Vector3
}> = []
let physicsEdges: Array<{
  line: THREE.Line
  source: THREE.Mesh
  target: THREE.Mesh
  weight: number
}> = []
let spinRings: THREE.Mesh[] = []
let physicsNodeMap = new Map<THREE.Mesh, (typeof physicsNodes)[number]>()
let physicsFrameSkip = 0
const _tempVec3 = new THREE.Vector3()
let isDragging = false
let dragStart = { x: 0, y: 0 }
let rotation = { x: -0.3, y: 0.5 }
let targetRotation = { x: -0.3, y: 0.5 }
let cameraDistance = 680

const BG_COLOR = 0xe0e8f2

function initScene() {
  if (!containerRef.value) return
  disposeScene()

  const { width, height } = getSize()
  scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(BG_COLOR, 0.001)

  camera = new THREE.PerspectiveCamera(55, width / height, 1, 3000)
  camera.position.set(0, 0, cameraDistance)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
  renderer.setSize(width, height)
  renderer.setClearColor(BG_COLOR, 1)
  containerRef.value.appendChild(renderer.domElement)

  const ambient = new THREE.AmbientLight(0xffffff, 1.0)
  scene.add(ambient)
  const key = new THREE.PointLight(0x60a5fa, 200, 1400)
  key.position.set(200, 300, 280)
  scene.add(key)
  const fill = new THREE.PointLight(0x93c5fd, 120, 1100)
  fill.position.set(-240, -180, -280)
  scene.add(fill)
  const rim = new THREE.PointLight(0xbfdbfe, 80, 900)
  rim.position.set(0, -120, 200)
  scene.add(rim)

  nodeGroup = new THREE.Group()
  edgeGroup = new THREE.Group()
  edgeFlowGroup = new THREE.Group()
  shellGroup = new THREE.Group()
  scene.add(edgeGroup)
  scene.add(edgeFlowGroup)
  scene.add(nodeGroup)
  scene.add(shellGroup)
  createSphereShell()
  drawGraph()
  bindCanvasEvents()
  animate()
}

function drawGraph() {
  if (!scene) return
  clearGroup(nodeGroup)
  clearGroup(edgeGroup)
  clearGroup(edgeFlowGroup)
  nodeMeshes = []
  edgeLines = []
  physicsNodes = []
  physicsEdges = []
  flowParticles = []
  spinRings = []
  physicsNodeMap.clear()

  const nodeMap = new Map<string, Panorama3DNode>()
  graph.value.nodes.forEach(node => nodeMap.set(node.id, node))
  const meshMap = new Map<string, THREE.Mesh>()

  for (const node of graph.value.nodes) {
    const isCenter = node.ring === 'center'
    const isPost = node.ring === 'post'
    const seg = isCenter ? 48 : 32
    const geometry = new THREE.SphereGeometry(node.radius, seg, seg)

    // 玻璃金属混合材质
    const mat = new THREE.MeshPhysicalMaterial({
      color: node.color,
      emissive: node.color,
      emissiveIntensity: isCenter ? 0.55 : 0.28,
      roughness: isCenter ? 0.08 : 0.12,
      metalness: isCenter ? 0.55 : 0.35,
      clearcoat: isCenter ? 0.4 : 0.2,
      clearcoatRoughness: 0.1,
      specularIntensity: 0.6,
      specularColor: new THREE.Color(0xffffff),
    })
    const mesh = new THREE.Mesh(geometry, mat)
    mesh.position.copy(toVector(node.position))
    mesh.userData.node = node
    nodeGroup.add(mesh)
    nodeMeshes.push(mesh)
    meshMap.set(node.id, mesh)
    const pn = { mesh, node, base: mesh.position.clone(), velocity: new THREE.Vector3() }
    physicsNodes.push(pn)
    physicsNodeMap.set(mesh, pn)

    if (shouldShowPanoramaNodeLabel(node, props.layoutMode)) {
      nodeGroup.add(createNodeLabel(node, mesh))
    }

    // 内核亮点
    const coreGeo = new THREE.SphereGeometry(node.radius * 0.38, 16, 12)
    const coreMat = new THREE.MeshBasicMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: isCenter ? 0.45 : 0.3,
      depthWrite: false,
    })
    const core = new THREE.Mesh(coreGeo, coreMat)
    core.position.copy(mesh.position)
    core.userData.follow = mesh
    nodeGroup.add(core)

    // 内层光晕（较亮）
    const innerHaloGeo = new THREE.SphereGeometry(node.radius * (isCenter ? 2.0 : 1.6), 20, 14)
    const innerHaloMat = new THREE.MeshBasicMaterial({
      color: node.color,
      transparent: true,
      opacity: isCenter ? 0.22 : isPost ? 0.13 : 0.09,
      depthWrite: false,
    })
    const innerHalo = new THREE.Mesh(innerHaloGeo, innerHaloMat)
    innerHalo.position.copy(mesh.position)
    innerHalo.userData.follow = mesh
    nodeGroup.add(innerHalo)

    // 外层光晕（较淡、较大）
    const outerHaloGeo = new THREE.SphereGeometry(node.radius * (isCenter ? 3.2 : 2.6), 16, 10)
    const outerHaloMat = new THREE.MeshBasicMaterial({
      color: node.color,
      transparent: true,
      opacity: isCenter ? 0.1 : isPost ? 0.06 : 0.04,
      depthWrite: false,
    })
    const outerHalo = new THREE.Mesh(outerHaloGeo, outerHaloMat)
    outerHalo.position.copy(mesh.position)
    outerHalo.userData.follow = mesh
    nodeGroup.add(outerHalo)

    // 中心节点光环
    if (isCenter) {
      const ringGeo = new THREE.TorusGeometry(node.radius * 2.6, 0.24, 16, 64)
      const ringMat = new THREE.MeshBasicMaterial({
        color: node.color,
        transparent: true,
        opacity: 0.5,
        depthWrite: false,
      })
      const ringMesh = new THREE.Mesh(ringGeo, ringMat)
      ringMesh.position.copy(mesh.position)
      ringMesh.userData.follow = mesh
      ringMesh.userData.spinAxis = 'z'
      nodeGroup.add(ringMesh)
      spinRings.push(ringMesh)

      const ring2Geo = new THREE.TorusGeometry(node.radius * 2.25, 0.14, 12, 56)
      const ring2Mat = new THREE.MeshBasicMaterial({
        color: 0xbfdbfe,
        transparent: true,
        opacity: 0.32,
        depthWrite: false,
      })
      const ring2Mesh = new THREE.Mesh(ring2Geo, ring2Mat)
      ring2Mesh.position.copy(mesh.position)
      ring2Mesh.rotation.x = Math.PI / 3
      ring2Mesh.userData.follow = mesh
      ring2Mesh.userData.spinAxis = 'tilt'
      nodeGroup.add(ring2Mesh)
      spinRings.push(ring2Mesh)

      // 第三层细光环
      const ring3Geo = new THREE.TorusGeometry(node.radius * 2.85, 0.08, 8, 48)
      const ring3Mat = new THREE.MeshBasicMaterial({
        color: 0xffffff,
        transparent: true,
        opacity: 0.2,
        depthWrite: false,
      })
      const ring3Mesh = new THREE.Mesh(ring3Geo, ring3Mat)
      ring3Mesh.position.copy(mesh.position)
      ring3Mesh.rotation.x = -Math.PI / 4
      ring3Mesh.rotation.y = Math.PI / 3
      ring3Mesh.userData.follow = mesh
      ring3Mesh.userData.spinAxis = 'xy'
      nodeGroup.add(ring3Mesh)
      spinRings.push(ring3Mesh)
    }
  }

  for (const edge of graph.value.edges) {
    const source = meshMap.get(edge.source)
    const target = meshMap.get(edge.target)
    if (!source || !target) continue

    const lineGeo = new THREE.BufferGeometry().setFromPoints([source.position, target.position])
    const lineMat = new THREE.LineBasicMaterial({
      color: edge.color,
      transparent: true,
      opacity: Math.max(0.12, edge.weight * 0.35),
      depthWrite: false,
    })
    const line = new THREE.Line(lineGeo, lineMat)
    edgeGroup.add(line)
    edgeLines.push(line)
    physicsEdges.push({ line, source, target, weight: edge.weight })

    const particleCount = Math.max(2, Math.floor(edge.weight * 6))
    const positions = new Float32Array(particleCount * 3)
    const speeds = new Float32Array(particleCount)
    for (let i = 0; i < particleCount; i++) {
      speeds[i] = i / particleCount
      const t = speeds[i]
      positions[i * 3] = source.position.x + (target.position.x - source.position.x) * t
      positions[i * 3 + 1] = source.position.y + (target.position.y - source.position.y) * t
      positions[i * 3 + 2] = source.position.z + (target.position.z - source.position.z) * t
    }
    const flowGeo = new THREE.BufferGeometry()
    flowGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    const flowMat = new THREE.PointsMaterial({
      color: edge.color,
      size: 1.6,
      transparent: true,
      opacity: Math.max(0.25, edge.weight * 0.5),
    })
    const flowPoints = new THREE.Points(flowGeo, flowMat)
    edgeFlowGroup.add(flowPoints)
    flowParticles.push({ points: flowPoints, positions, speeds, source, target })
  }
}

function createSphereShell() {
  if (!scene) return
  clearGroup(shellGroup)

  const shellGeo = new THREE.SphereGeometry(400, 36, 20)
  const shellMat = new THREE.MeshBasicMaterial({
    color: 0x3b82f6,
    transparent: true,
    opacity: 0.06,
    wireframe: true,
    depthWrite: false,
  })
  shellGroup.add(new THREE.Mesh(shellGeo, shellMat))

  const count = 160
  const positions: number[] = []
  const colors = new Float32Array(count * 3)
  const palette = ['#60a5fa', '#3b82f6', '#93c5fd', '#818cf8', '#2563eb'].map(color => new THREE.Color(color))
  const radius = 395
  for (let i = 0; i < count; i++) {
    const y = 1 - (2 * (i + 0.5)) / count
    const ringRadius = Math.sqrt(Math.max(0, 1 - y * y))
    const theta = i * Math.PI * (3 - Math.sqrt(5))
    positions.push(
      Math.cos(theta) * ringRadius * radius,
      y * radius,
      Math.sin(theta) * ringRadius * radius,
    )
    palette[(i * 7 + Math.round((y + 1) * 2)) % palette.length].toArray(colors, i * 3)
  }
  const particleGeo = new THREE.BufferGeometry()
  particleGeo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  particleGeo.setAttribute('color', new THREE.BufferAttribute(colors, 3))
  const particleMat = new THREE.PointsMaterial({
    size: 2.8,
    vertexColors: true,
    transparent: true,
    opacity: 0.42,
  })
  shellStarParticles = new THREE.Points(particleGeo, particleMat)
  shellGroup.add(shellStarParticles)
}

function animate() {
  if (!renderer || !scene || !camera) return
  frameId = requestAnimationFrame(animate)

  targetRotation.x += (rotation.x - targetRotation.x) * 0.08
  targetRotation.y += (rotation.y - targetRotation.y) * 0.08
  physicsFrameSkip = (physicsFrameSkip + 1) % 2
  if (physicsFrameSkip === 0) stepPhysics(performance.now() * 0.001)
  nodeGroup.rotation.x = targetRotation.x
  nodeGroup.rotation.y = targetRotation.y
  edgeGroup.rotation.copy(nodeGroup.rotation)
  edgeFlowGroup.rotation.copy(nodeGroup.rotation)
  shellGroup.rotation.copy(nodeGroup.rotation)
  shellGroup.rotation.y += 0.0012

  if (shellStarParticles) {
    const m = shellStarParticles.material as THREE.PointsMaterial
    m.opacity = 0.38 + Math.sin(performance.now() * 0.0005) * 0.06
  }

  // spin rings (cached)
  for (let i = 0; i < spinRings.length; i++) {
    const ring = spinRings[i]
    if (ring.userData.spinAxis === 'z') {
      ring.rotation.z += 0.012
      ring.rotation.x += 0.006
    } else if (ring.userData.spinAxis === 'xy') {
      ring.rotation.x += 0.008
      ring.rotation.y -= 0.014
    } else {
      ring.rotation.z += 0.016
      ring.rotation.y -= 0.01
    }
  }

  // flow particles along edges
  for (const flow of flowParticles) {
    const sx = flow.source.position.x, sy = flow.source.position.y, sz = flow.source.position.z
    const tx = flow.target.position.x, ty = flow.target.position.y, tz = flow.target.position.z
    for (let i = 0; i < flow.speeds.length; i++) {
      flow.speeds[i] = (flow.speeds[i] + 0.004) % 1
      const t = flow.speeds[i]
      flow.positions[i * 3] = sx + (tx - sx) * t
      flow.positions[i * 3 + 1] = sy + (ty - sy) * t
      flow.positions[i * 3 + 2] = sz + (tz - sz) * t
    }
    const attr = flow.points.geometry.getAttribute('position') as THREE.BufferAttribute
    attr.needsUpdate = true
  }

  camera.position.z += (cameraDistance - camera.position.z) * 0.08
  renderer.render(scene, camera)
}

function stepPhysics(elapsed: number) {
  if (physicsNodes.length === 0) return

  for (const item of physicsNodes) {
    if (item.node.ring === 'center') continue
    // spring to base position
    _tempVec3.copy(item.base).sub(item.mesh.position).multiplyScalar(0.012)
    // subtle breathe
    const breathe = Math.sin(elapsed * 1.8 + item.base.length() * 0.01) * 0.022
    _tempVec3.x += item.base.x / (item.base.length() || 1) * breathe
    _tempVec3.y += item.base.y / (item.base.length() || 1) * breathe
    _tempVec3.z += item.base.z / (item.base.length() || 1) * breathe
    item.velocity.add(_tempVec3)
  }

  for (const edge of physicsEdges) {
    _tempVec3.copy(edge.target.position).sub(edge.source.position)
    const distance = Math.max(1, _tempVec3.length())
    const ideal = 92 + edge.weight * 44
    const force = (distance - ideal) * 0.0009 * edge.weight
    _tempVec3.multiplyScalar(force / distance)
    const sourceNode = edge.source.userData.node as Panorama3DNode
    const targetNode = edge.target.userData.node as Panorama3DNode
    const sp = physicsNodeMap.get(edge.source)
    const tp = physicsNodeMap.get(edge.target)
    if (sp && sourceNode.ring !== 'center') sp.velocity.add(_tempVec3)
    if (tp && targetNode.ring !== 'center') tp.velocity.add(_tempVec3.clone().multiplyScalar(-1))
  }

  for (let i = 0; i < physicsNodes.length; i += 1) {
    const a = physicsNodes[i]
    if (a.node.ring === 'center') continue
    for (let j = i + 1; j < physicsNodes.length; j += 1) {
      const b = physicsNodes[j]
      if (b.node.ring === 'center') continue
      _tempVec3.copy(a.mesh.position).sub(b.mesh.position)
      const distance = Math.max(1, _tempVec3.length())
      if (distance > 64) continue
      _tempVec3.multiplyScalar((64 - distance) * 0.0007 / distance)
      a.velocity.add(_tempVec3)
      b.velocity.add(_tempVec3.clone().multiplyScalar(-1))
    }
  }

  for (const item of physicsNodes) {
    item.velocity.multiplyScalar(0.88)
    item.mesh.position.add(item.velocity)
  }

  for (const child of nodeGroup.children) {
    const follow = child.userData.follow as THREE.Mesh | undefined
    if (follow) {
      child.position.copy(follow.position)
      child.position.y += child.userData.labelOffset || 0
    }
  }

  for (const edge of physicsEdges) {
    const geometry = edge.line.geometry as THREE.BufferGeometry
    const attr = geometry.getAttribute('position') as THREE.BufferAttribute
    attr.setXYZ(0, edge.source.position.x, edge.source.position.y, edge.source.position.z)
    attr.setXYZ(1, edge.target.position.x, edge.target.position.y, edge.target.position.z)
    attr.needsUpdate = true
    geometry.computeBoundingSphere()
  }
}

function bindCanvasEvents() {
  if (!renderer) return
  const canvas = renderer.domElement
  canvas.addEventListener('pointerdown', handlePointerDown)
  canvas.addEventListener('pointermove', handlePointerMove)
  canvas.addEventListener('pointerup', handlePointerUp)
  canvas.addEventListener('pointerleave', handlePointerLeave)
  canvas.addEventListener('wheel', handleWheel, { passive: false })
}

function unbindCanvasEvents() {
  if (!renderer) return
  const canvas = renderer.domElement
  canvas.removeEventListener('pointerdown', handlePointerDown)
  canvas.removeEventListener('pointermove', handlePointerMove)
  canvas.removeEventListener('pointerup', handlePointerUp)
  canvas.removeEventListener('pointerleave', handlePointerLeave)
  canvas.removeEventListener('wheel', handleWheel)
}

function handlePointerDown(event: PointerEvent) {
  isDragging = true
  dragStart = { x: event.clientX, y: event.clientY }
}

function handlePointerMove(event: PointerEvent) {
  updateTooltip(event)
  if (!isDragging) return
  const dx = event.clientX - dragStart.x
  const dy = event.clientY - dragStart.y
  dragStart = { x: event.clientX, y: event.clientY }
  rotation.y += dx * 0.006
  rotation.x += dy * 0.004
  rotation.x = Math.max(-1.1, Math.min(0.9, rotation.x))
}

function handlePointerUp(event: PointerEvent) {
  const moved = Math.abs(event.clientX - dragStart.x) + Math.abs(event.clientY - dragStart.y)
  isDragging = false
  if (moved < 4) {
    const hit = pickNode(event)
    if (hit) {
      selectedId.value = hit.id
      emit('node-click', hit)
    }
  }
}

function handlePointerLeave() {
  isDragging = false
  tooltip.value.show = false
}

function handleWheel(event: WheelEvent) {
  event.preventDefault()
  cameraDistance = Math.max(300, Math.min(1000, cameraDistance + event.deltaY * 0.42))
}

function updateTooltip(event: PointerEvent) {
  const hit = pickNode(event)
  if (!hit) {
    tooltip.value.show = false
    return
  }
  tooltip.value = {
    show: true,
    x: event.clientX + 14,
    y: event.clientY + 14,
    label: hit.label,
    meta: `${getRingLabel(hit.ring)}${hit.level ? ` / L${hit.level}` : ''}${hit.category ? ` / ${hit.category}` : ''}`,
  }
}

function pickNode(event: PointerEvent): Panorama3DNode | null {
  if (!renderer || !camera) return null
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObjects(nodeMeshes, false)
  return hits[0]?.object.userData.node || null
}

function resetCamera() {
  rotation = { x: -0.3, y: 0.5 }
  cameraDistance = 680
}

function resize() {
  if (!renderer || !camera) return
  const { width, height } = getSize()
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
}

function getSize() {
  const rect = containerRef.value?.getBoundingClientRect()
  return {
    width: Math.max(360, Math.floor(rect?.width || 960)),
    height: Math.max(360, Math.floor(rect?.height || 640)),
  }
}

function toVector(position: { x: number; y: number; z: number }) {
  return new THREE.Vector3(position.x, position.y, position.z)
}

function createNodeLabel(node: Panorama3DNode, target: THREE.Mesh): THREE.Sprite {
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')!
  const text = node.label.length > 12 ? `${node.label.slice(0, 12)}...` : node.label
  const fontSize = node.ring === 'center' ? 28 : node.ring === 'stack' ? 24 : 22
  context.font = `600 ${fontSize}px Arial, Microsoft YaHei, sans-serif`
  const width = Math.ceil(context.measureText(text).width) + 24
  canvas.width = width
  canvas.height = fontSize + 18
  context.font = `600 ${fontSize}px Arial, Microsoft YaHei, sans-serif`
  context.fillStyle = 'rgba(255, 255, 255, 0.92)'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = '#0f172a'
  context.textBaseline = 'middle'
  context.fillText(text, 12, canvas.height / 2)

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture, transparent: true, depthWrite: false }))
  sprite.name = 'node-label'
  sprite.scale.set(canvas.width * 0.34, canvas.height * 0.34, 1)
  sprite.position.copy(target.position)
  sprite.position.y += node.radius + 12
  sprite.userData.follow = target
  sprite.userData.labelOffset = node.radius + 12
  return sprite
}

function clearGroup(group: THREE.Group) {
  for (const child of [...group.children]) {
    group.remove(child)
    const mesh = child as THREE.Mesh
    mesh.geometry?.dispose()
    const material = mesh.material as THREE.Material | THREE.Material[] | undefined
    if (Array.isArray(material)) material.forEach(item => item.dispose())
    else material?.dispose()
  }
}

function disposeScene() {
  cancelAnimationFrame(frameId)
  unbindCanvasEvents()
  if (renderer?.domElement.parentElement) renderer.domElement.parentElement.removeChild(renderer.domElement)
  clearGroup(nodeGroup)
  clearGroup(edgeGroup)
  clearGroup(edgeFlowGroup)
  clearGroup(shellGroup)
  renderer?.dispose()
  renderer = null
  scene = null
  camera = null
  shellStarParticles = null
  spinRings = []
  physicsNodeMap.clear()
  nodeMeshes = []
  flowParticles = []
}

function getRingLabel(ring: string) {
  const map: Record<string, string> = {
    center: '中心岗位',
    stack: '技术栈',
    post: '岗位方向',
    ability: '能力标签',
    skill: '技能点',
    other: '关联节点',
  }
  return map[ring] || ring
}

watch(() => [props.graphData, props.layoutMode, props.focusNodeId], () => {
  nextTick(drawGraph)
})

onMounted(() => {
  nextTick(initScene)
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  disposeScene()
})

defineExpose({ resetCamera })
</script>

<template>
  <div ref="containerRef" class="post-panorama-3d">
    <div class="graph-vignette" />
    <button class="camera-reset" type="button" title="重置视角" @click="resetCamera">
      重置视角
    </button>
    <div class="graph-empty" v-if="!graph.nodes.length">
      暂无图谱数据
    </div>
    <div
      v-if="tooltip.show"
      class="graph-tooltip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
    >
      <strong>{{ tooltip.label }}</strong>
      <span>{{ tooltip.meta }}</span>
    </div>
  </div>
</template>

<style scoped>
.post-panorama-3d {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.post-panorama-3d :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
}

.post-panorama-3d :deep(canvas:active) {
  cursor: grabbing;
}

.graph-vignette {
  position: absolute;
  inset: 0;
  pointer-events: none;
  box-shadow: inset 0 0 80px rgba(37, 99, 235, 0.06);
  z-index: 1;
}

.camera-reset {
  position: absolute;
  right: 14px;
  bottom: 14px;
  z-index: 4;
  height: 32px;
  padding: 0 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.48);
  color: var(--app-primary, #2563eb);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  backdrop-filter: blur(10px);
  transition: border-color 0.2s, color 0.2s, background 0.2s;
}

.camera-reset:hover {
  border-color: rgba(37, 99, 235, 0.28);
  color: #1d4ed8;
  background: rgba(255, 255, 255, 0.68);
}

.graph-empty {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 14px;
}

.graph-tooltip {
  position: fixed;
  z-index: 10000;
  min-width: 150px;
  max-width: 260px;
  padding: 10px 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  color: #475569;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(14px);
  pointer-events: none;
}

.graph-tooltip strong {
  display: block;
  margin-bottom: 4px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.graph-tooltip span {
  font-size: 11px;
  color: #64748b;
}
</style>
