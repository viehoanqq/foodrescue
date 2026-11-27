import { useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Empty, Form, Image, Input, InputNumber, Modal, Row, Select, Space, Statistic, Table, Tag, Typography, Upload } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { Link, useParams } from 'react-router-dom';
import { money, updateDemo } from './data';
import { useDemo } from './useDemo';
import { confirmDialog, toast } from '../components/feedback';

const { Title, Text } = Typography;
const label = { AVAILABLE: 'Đang bán', CANCELLED: 'Đã hủy', PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', LOCKED: 'Đã khóa', CONFIRMED: 'Đã thanh toán', COMPLETED: 'Đã giao', PAID: 'Đã thanh toán', UNPAID: 'Chưa thanh toán', REFUNDED: 'Đã hoàn', PENDING_PAYMENT: 'Chờ thanh toán' };
function Status({ value }) { return <Tag color={['APPROVED','AVAILABLE','CONFIRMED','COMPLETED','PAID'].includes(value) ? 'green' : value === 'CANCELLED' || value === 'LOCKED' ? 'red' : 'orange'}>{label[value] || value}</Tag>; }
function Page({ title, extra, children }) { return <div style={{ padding: '24px', maxWidth: 1180, margin: '0 auto' }}><Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}><Title level={3} style={{ margin: 0 }}>{title}</Title>{extra}</Space>{children}</div>; }
function audit(data, action, actor = 'Tài khoản mẫu') { data.audit.unshift({ id: Date.now(), action, actor, time: new Date().toLocaleString('vi-VN') }); }

export function DemoOrders({ store=false }) {
  const d=useDemo(); const [filter,setFilter]=useState(null); const rows=d.orders.filter(o=>(!store||o.storeId===1)&&(!filter||o.status===filter));
  return <Page title={store?'Đơn cửa hàng':'Đơn của tôi'} extra={<Select allowClear placeholder="Trạng thái" style={{width:190}} value={filter} onChange={setFilter} options={['PENDING_PAYMENT','CONFIRMED','COMPLETED','CANCELLED'].map(v=>({value:v,label:label[v]||v}))} />}><Table rowKey="id" dataSource={rows} columns={[{title:'Mã đơn',render:(_,r)=><Link to={store?`/store/orders/${r.id}`:`/customer/orders/${r.id}`}>#{r.id}</Link>},{title:'Cửa hàng',render:(_,r)=>d.stores.find(s=>s.id===r.storeId)?.name},{title:'Ngày tạo',dataIndex:'createdAt'},{title:'Tổng tiền',render:(_,r)=>money(r.total)},{title:'Trạng thái',render:(_,r)=><Status value={r.status} />},{title:'Thanh toán',render:(_,r)=><Status value={r.paymentStatus} />}]} /></Page>;
}
export function DemoOrderDetail({ store=false }) {
  const {orderId}=useParams(); const d=useDemo(); const o=d.orders.find(x=>x.id===Number(orderId)); if(!o)return <Empty description="Không tìm thấy đơn"/>;
  const pay=()=>updateDemo(x=>{const row=x.orders.find(a=>a.id===o.id);row.paymentStatus='PAID';row.status='CONFIRMED';audit(x,'PAYMENT_SUCCEEDED');});
  const cancel=()=>updateDemo(x=>{const row=x.orders.find(a=>a.id===o.id);row.status='CANCELLED';x.batches.forEach(b=>{const item=row.items.find(i=>i.batchId===b.id);if(item)b.remaining+=item.quantity;});if(row.paymentStatus==='PAID'){row.paymentStatus='REFUND_PENDING';x.refunds.push({id:Date.now(),orderId:row.id,amount:row.total,status:'PENDING'});}audit(x,'ORDER_CANCELLED');});
  return <Page title={`Đơn #${o.id}`}><Card><Descriptions bordered column={1} items={[{key:1,label:'Cửa hàng',children:d.stores.find(s=>s.id===o.storeId)?.name},{key:2,label:'Trạng thái',children:<Status value={o.status}/>},{key:3,label:'Thanh toán',children:<Status value={o.paymentStatus}/>},{key:4,label:'Mã nhận hàng',children:o.paymentStatus==='PAID'?o.pickupCode:'Có sau khi thanh toán'},{key:5,label:'Tổng tiền',children:money(o.total)}]} /><Table style={{marginTop:20}} pagination={false} rowKey="batchId" dataSource={o.items} columns={[{title:'Túi',render:(_,r)=>d.batches.find(b=>b.id===r.batchId)?.title},{title:'Số lượng',dataIndex:'quantity'},{title:'Giá',render:(_,r)=>money(r.price)}]} /><Space style={{marginTop:16}}>{!store&&o.status==='PENDING_PAYMENT'&&<Button type="primary" onClick={() => confirmDialog({ title: 'Xác nhận thanh toán mô phỏng?', content: `Đơn #${o.id} · ${money(o.total)}`, okText: 'Xác nhận', cancelText: 'Quay lại', tone: 'eco', onOk: pay })}>Mô phỏng thanh toán</Button>}{['PENDING_PAYMENT','CONFIRMED'].includes(o.status)&&<Button danger onClick={() => confirmDialog({ title: `Hủy đơn #${o.id}?`, content: o.paymentStatus === 'PAID' ? 'Đơn sẽ chuyển sang chờ hoàn tiền.' : 'Số túi sẽ được trả lại kho.', okText: 'Hủy đơn', cancelText: 'Giữ đơn', danger: true, onOk: cancel })}>Hủy đơn</Button>}</Space></Card></Page>;
}
export function DemoPickup() { const d=useDemo(); const [code,setCode]=useState(''); const order=d.orders.find(o=>o.pickupCode===code.trim().toUpperCase()); return <Page title="Giao hàng tại quầy"><Card><Space direction="vertical"><Text>Nhập mã nhận hàng khách cung cấp.</Text><Input.Search placeholder="Ví dụ FR101" value={code} onChange={e=>setCode(e.target.value)} style={{width:280}} />{code&&(!order?<Alert type="warning" message="Không tìm thấy đơn"/>:<><Text>Đơn #{order.id} · {money(order.total)} · <Status value={order.status}/></Text><Button type="primary" disabled={order.status!=='CONFIRMED'} onClick={()=>confirmDialog({title:'X\u00e1c nh\u1eadn \u0111\u00e3 giao t\u00fai?',content:`\u0110\u01a1n #${order.id} s\u1ebd chuy\u1ec3n sang ho\u00e0n th\u00e0nh. H\u00e3y ki\u1ec3m tra t\u00fai v\u00e0 m\u00e3 nh\u1eadn h\u00e0ng.`,okText:'\u0110\u00e3 giao',cancelText:'Quay l\u1ea1i',tone:'eco',onOk:()=>{updateDemo(x=>{x.orders.find(o=>o.id===order.id).status='COMPLETED';audit(x,'ORDER_COMPLETED');});toast.success('\u0110\u00e3 giao h\u00e0ng');}})}>Xác nhận giao</Button></>)}</Space></Card></Page>; }
export function DemoRevenue() { const d=useDemo(); const orders=d.orders.filter(o=>o.storeId===1&&o.paymentStatus==='PAID'); const gross=orders.reduce((n,o)=>n+o.total,0); return <Page title="Doanh thu cửa hàng"><Row gutter={16}><Col span={8}><Card><Statistic title="Doanh thu" value={gross} suffix="đ"/></Card></Col><Col span={8}><Card><Statistic title="Hoa hồng" value={Math.round(gross*.15)} suffix="đ"/></Card></Col><Col span={8}><Card><Statistic title="Thực nhận" value={Math.round(gross*.85)} suffix="đ"/></Card></Col></Row><Table style={{marginTop:16}} rowKey="id" dataSource={d.payouts.filter(p=>p.storeId===1)} columns={[{title:'Kỳ',dataIndex:'period'},{title:'Số tiền',render:(_,r)=>money(r.amount)},{title:'Trạng thái',render:(_,r)=><Status value={r.status}/>}]} /></Page>; }
export function DemoReviews() { const d=useDemo(); return <Page title="Đánh giá cửa hàng"><Table rowKey="id" dataSource={d.reviews.filter(r=>r.storeId===1)} columns={[{title:'Đơn hàng',dataIndex:'orderId'},{title:'Số sao',dataIndex:'rating'},{title:'Nhận xét',dataIndex:'comment'}]} locale={{emptyText:'Chưa có đánh giá'}} /></Page>; }
export function DemoStoreCategories() {
  const data = useDemo();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState(null);
  const [open, setOpen] = useState(false);
  const [imageUrl, setImageUrl] = useState('');
  const categories = data.categories.filter(category => category.storeId === 1);

  function showEditor(category = null) {
    setEditing(category);
    setImageUrl(category?.imageUrl || '');
    form.setFieldsValue({ name: category?.name || '', description: category?.description || '' });
    setOpen(true);
  }

  function chooseImage(file) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      toast.error('Chỉ nhận ảnh JPEG, PNG hoặc WebP');
      return false;
    }
    if (file.size > 500 * 1024) {
      toast.error('Ảnh mẫu không được vượt quá 500 KB');
      return false;
    }
    const reader = new FileReader();
    reader.onload = () => setImageUrl(String(reader.result));
    reader.onerror = () => toast.error('Không đọc được ảnh');
    reader.readAsDataURL(file);
    return false;
  }

  function save(values) {
    const name = values.name.trim();
    if (!imageUrl) { toast.error('Chọn ảnh danh mục'); return; }
    if (categories.some(category => category.name.toLocaleLowerCase('vi-VN') === name.toLocaleLowerCase('vi-VN') && category.id !== editing?.id)) {
      toast.error('Tên danh mục đã có trong cửa hàng');
      return;
    }
    updateDemo(next => {
      const details = { name, description: values.description.trim(), imageUrl };
      if (editing) Object.assign(next.categories.find(category => category.id === editing.id), details);
      else next.categories.push({ id: Date.now(), storeId: 1, sortOrder: 0, ...details });
    });
    setOpen(false);
    setEditing(null);
    form.resetFields();
    toast.success('Đã lưu danh mục');
  }

  function confirmDelete(category) {
    if (data.batches.some(batch => batch.storeId === 1 && batch.categoryId === category.id)) {
      toast.warning('Danh mục đang có túi giải cứu, không thể xóa');
      return;
    }
    confirmDialog({
      title: `Xóa danh mục “${category.name}”?`,
      content: 'Danh mục sẽ bị xóa khỏi dữ liệu mẫu của cửa hàng.',
      okText: 'Xóa', danger: true, cancelText: 'Hủy',
      onOk: () => updateDemo(next => { next.categories = next.categories.filter(row => row.id !== category.id); }),
    });
  }

  return <Page title="Danh mục của cửa hàng" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => showEditor()}>Thêm danh mục</Button>}>
    <Table rowKey="id" dataSource={categories} columns={[
      { title: 'Ảnh', dataIndex: 'imageUrl', render: (url, category) => url && <Image src={url} alt={category.name} width={80} height={54} style={{ objectFit: 'cover', borderRadius: 8 }} /> },
      { title: 'Tên danh mục', dataIndex: 'name' },
      { title: 'Mô tả', dataIndex: 'description', ellipsis: true },
      { title: 'Số túi', render: (_, category) => data.batches.filter(batch => batch.storeId === 1 && batch.categoryId === category.id).length },
      { title: 'Thao tác', render: (_, category) => <Space><Button icon={<EditOutlined />} onClick={() => showEditor(category)}>Sửa</Button>
        <Button danger icon={<DeleteOutlined />} onClick={() => confirmDelete(category)}>Xóa</Button></Space> },
    ]} />
    <Modal title={editing ? 'Sửa danh mục' : 'Thêm danh mục'} open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()}
      okText={editing ? 'Lưu thay đổi' : 'Thêm danh mục'} cancelText="Hủy" destroyOnClose>
      <Form form={form} layout="vertical" onFinish={save}>
        <Form.Item name="name" label="Tên danh mục" rules={[{ required: true, whitespace: true, message: 'Nhập tên danh mục' }, { max: 100 }]}>
          <Input maxLength={100} placeholder="Ví dụ: Bánh mì" />
        </Form.Item>
        <Form.Item name="description" label="Mô tả" rules={[{ required: true, whitespace: true, message: 'Nhập mô tả danh mục' }, { max: 1000 }]}>
          <Input.TextArea rows={3} maxLength={1000} showCount placeholder="Giới thiệu ngắn về các túi trong danh mục" />
        </Form.Item>
        <Form.Item label="Hình ảnh" required>
          <Upload accept="image/jpeg,image/png,image/webp" beforeUpload={chooseImage} showUploadList={false}>
            <Button icon={<UploadOutlined />}>Chọn ảnh</Button>
          </Upload>
          {imageUrl && <div style={{ marginTop: 12 }}><Image src={imageUrl} alt="Ảnh danh mục" width={180} style={{ borderRadius: 8 }} /></div>}
          <Text type="secondary">JPEG, PNG hoặc WebP, tối đa 500 KB vì dữ liệu mẫu lưu trong trình duyệt.</Text>
        </Form.Item>
      </Form>
    </Modal>
  </Page>;
}
export function DemoBatches() { const d=useDemo(); const [open,setOpen]=useState(false); const [editing,setEditing]=useState(null); const [form]=Form.useForm(); const rows=d.batches.filter(b=>b.storeId===1); const show=b=>{setEditing(b||null);form.setFieldsValue(b||{categoryId:1,originalPrice:80000,rescuePrice:30000,remaining:5,pickup:'20:00–21:00'});setOpen(true);}; return <Page title="Túi giải cứu của cửa hàng" extra={<Button type="primary" icon={<PlusOutlined/>} onClick={()=>show()}>Tạo túi</Button>}><Table rowKey="id" dataSource={rows} columns={[{title:'Túi',dataIndex:'title'},{title:'Giá',render:(_,r)=>money(r.rescuePrice)},{title:'Còn',dataIndex:'remaining'},{title:'Giờ nhận',dataIndex:'pickup'},{title:'Trạng thái',render:(_,r)=><Status value={r.status}/>},{title:'Thao tác',render:(_,r)=><Space><Button icon={<EditOutlined/>} onClick={()=>show(r)}>Sửa</Button><Button danger disabled={r.status==='CANCELLED'} onClick={()=>confirmDialog({title:'H\u1ee7y t\u00fai gi\u1ea3i c\u1ee9u?',content:`${r.title} s\u1ebd ng\u1eebng b\u00e1n.`,okText:'H\u1ee7y t\u00fai',cancelText:'Gi\u1eef l\u1ea1i',danger:true,onOk:()=>updateDemo(x=>{x.batches.find(b=>b.id===r.id).status='CANCELLED';audit(x,'BATCH_CANCELLED');})})}>Hủy</Button></Space>}]} /><Modal title={editing?'Sửa túi':'Tạo túi'} open={open} onOk={()=>form.submit()} onCancel={()=>setOpen(false)}><Form form={form} layout="vertical" onFinish={v=>{updateDemo(x=>{if(editing)Object.assign(x.batches.find(b=>b.id===editing.id),v);else x.batches.push({...v,id:Date.now(),storeId:1,status:'AVAILABLE'});audit(x,editing?'BATCH_UPDATED':'BATCH_CREATED');});setOpen(false);form.resetFields();}}><Form.Item name="title" label="Tên túi" rules={[{required:true}]}><Input/></Form.Item><Form.Item name="categoryId" label="Danh mục"><Select options={d.categories.filter(c=>c.storeId===1).map(c=>({value:c.id,label:c.name}))}/></Form.Item><Form.Item name="originalPrice" label="Giá gốc"><InputNumber min={1000} style={{width:'100%'}}/></Form.Item><Form.Item name="rescuePrice" label="Giá giải cứu"><InputNumber min={1000} style={{width:'100%'}}/></Form.Item><Form.Item name="remaining" label="Số lượng"><InputNumber min={0} style={{width:'100%'}}/></Form.Item><Form.Item name="pickup" label="Giờ nhận"><Input/></Form.Item></Form></Modal></Page>; }
