import { useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Empty, Form, Image, Input, InputNumber, Modal, Row, Select, Space, Statistic, Table, Tag, Typography, Upload, message } from 'antd';
import { CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, ShoppingCartOutlined, UploadOutlined } from '@ant-design/icons';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { money, readDemo, resetDemo, updateDemo } from './data';
import { useDemo } from './useDemo';
import { useAuth } from '../auth/AuthProvider';
import './demoCatalog.css';

const { Title, Text } = Typography;
const label = { AVAILABLE: 'Đang bán', CANCELLED: 'Đã hủy', PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', LOCKED: 'Đã khóa', CONFIRMED: 'Đã thanh toán', COMPLETED: 'Đã giao', PAID: 'Đã thanh toán', UNPAID: 'Chưa thanh toán', REFUNDED: 'Đã hoàn', PENDING_PAYMENT: 'Chờ thanh toán' };
function Status({ value }) { return <Tag color={['APPROVED','AVAILABLE','CONFIRMED','COMPLETED','PAID'].includes(value) ? 'green' : value === 'CANCELLED' || value === 'LOCKED' ? 'red' : 'orange'}>{label[value] || value}</Tag>; }
function Page({ title, extra, children }) { return <div style={{ padding: '24px', maxWidth: 1180, margin: '0 auto' }}><Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}><Title level={3} style={{ margin: 0 }}>{title}</Title>{extra}</Space>{children}</div>; }
function audit(data, action, actor = 'Tài khoản mẫu') { data.audit.unshift({ id: Date.now(), action, actor, time: new Date().toLocaleString('vi-VN') }); }

export function DemoHome() {
  const data = useDemo(); const {user}=useAuth(); const navigate=useNavigate(); const [params] = useSearchParams(); const [search, setSearch] = useState(params.get('q') || ''); const [category, setCategory] = useState(null); const [sort, setSort] = useState('new');
  const rows = data.batches.filter(b => b.status === 'AVAILABLE' && b.remaining > 0 && data.stores.some(s => s.id === b.storeId && s.status === 'APPROVED') && (!category || data.categories.some(c => c.id === b.categoryId && c.storeId === b.storeId && c.name === category)) && `${b.title} ${data.stores.find(s=>s.id===b.storeId)?.name}`.toLowerCase().includes(search.toLowerCase())).sort((a,b)=>sort==='price'?a.rescuePrice-b.rescuePrice:sort==='discount'?(a.rescuePrice/a.originalPrice)-(b.rescuePrice/b.originalPrice):b.id-a.id);
  return <div className="catalog-page"><div className="catalog-heading"><span className="home-eyebrow">KHÁM PHÁ MỖI NGÀY</span><h1>Túi giải cứu</h1><p>Chọn túi thực phẩm phù hợp từ các cửa hàng địa phương.</p></div><div className="catalog-filters"><Input.Search placeholder="Tìm túi hoặc cửa hàng..." value={search} onChange={e => setSearch(e.target.value)} allowClear /><Select placeholder="Tất cả danh mục" allowClear value={category} onChange={setCategory} options={[...new Set(data.categories.filter(c => data.stores.some(store => store.id === c.storeId && store.status === 'APPROVED')).map(c => c.name))].map(name => ({ value: name, label: name }))} /><Select value={sort} onChange={setSort} options={[{value:'new',label:'Mới đăng'},{value:'price',label:'Giá thấp trước'},{value:'discount',label:'Giảm nhiều nhất'}]} /></div><div className="catalog-result"><span>{rows.length} túi đang bán</span><Link to={user?'/customer/cart':'/login'}><ShoppingCartOutlined /> Xem giỏ hàng</Link></div><div className="catalog-grid">{rows.map(b => { const store = data.stores.find(s => s.id === b.storeId); return <article className="catalog-card" key={b.id}><Link to={`/batches/${b.id}`} className="catalog-media"><GiftOutlined /><span>-{Math.round((1-b.rescuePrice/b.originalPrice)*100)}%</span></Link><div className="catalog-card-content"><Text className="catalog-store">{store?.name}</Text><Link to={`/batches/${b.id}`}><h2>{b.title}</h2></Link><p>{store?.address}</p><p>Nhận hàng: {b.pickup} · Còn {b.remaining} túi</p><div className="catalog-price"><strong>{money(b.rescuePrice)}</strong><del>{money(b.originalPrice)}</del></div><Button type="primary" icon={<ShoppingCartOutlined />} onClick={() => { if(!user){navigate('/login');return;} const current=data.cart.find(i=>i.batchId===b.id); if(current&&current.quantity>=Math.min(5,b.remaining)){message.warning('Đã đạt số lượng tối đa');return;} updateDemo(d => { const item = d.cart.find(i => i.batchId === b.id); if (item) item.quantity++; else d.cart.push({ batchId: b.id, quantity: 1 }); }); message.success('Đã thêm vào giỏ'); }}>Thêm vào giỏ</Button></div></article>; })}</div>{!rows.length && <Empty description="Không tìm thấy túi phù hợp" />}</div>;
}
export function DemoBatchDetail() {
  const { batchId } = useParams(); const d = useDemo(); const {user}=useAuth(); const navigate=useNavigate(); const b = d.batches.find(x => x.id === Number(batchId)); if (!b) return <Empty description="Không tìm thấy túi" />;
  const store = d.stores.find(s => s.id === b.storeId);
  return <Page title={b.title}><Card><Descriptions column={1} bordered items={[{key:1,label:'Cửa hàng',children:store?.name},{key:2,label:'Địa chỉ',children:store?.address},{key:3,label:'Danh mục',children:d.categories.find(c=>c.id===b.categoryId && c.storeId===b.storeId)?.name},{key:4,label:'Giờ nhận',children:b.pickup},{key:5,label:'Giá gốc',children:money(b.originalPrice)},{key:6,label:'Giá giải cứu',children:money(b.rescuePrice)},{key:7,label:'Số lượng còn',children:b.remaining}]} /><Button style={{ marginTop: 16 }} type="primary" disabled={b.remaining < 1 || b.status !== 'AVAILABLE'} onClick={() => { if(!user){navigate('/login');return;} updateDemo(x => { const item=x.cart.find(i=>i.batchId===b.id); if(item)item.quantity++; else x.cart.push({batchId:b.id,quantity:1}); }); message.success('Đã thêm vào giỏ'); }}>Thêm vào giỏ</Button></Card></Page>;
}
export function DemoCart() {
  const d=useDemo(); const navigate=useNavigate(); const rows=d.cart.map(i=>({...i,batch:d.batches.find(b=>b.id===i.batchId)})).filter(i=>i.batch); const total=rows.reduce((n,i)=>n+i.quantity*i.batch.rescuePrice,0);
  const checkout=()=>{ if(!rows.length)return; const stores=[...new Set(rows.map(i=>i.batch.storeId))]; if(stores.length>1){message.warning('Mỗi đơn chỉ thuộc một cửa hàng. Vui lòng tách giỏ trước khi đặt.');return;} if(rows.some(i=>i.quantity>i.batch.remaining)){message.error('Số lượng còn lại không đủ');return;} const id=Date.now(); updateDemo(x=>{x.orders.unshift({id,customer:'Bạn',storeId:stores[0],items:rows.map(i=>({batchId:i.batchId,quantity:i.quantity,price:i.batch.rescuePrice})),total,status:'PENDING_PAYMENT',paymentStatus:'UNPAID',pickupCode:`FR${String(id).slice(-6)}`,createdAt:new Date().toLocaleString('vi-VN')}); x.batches.forEach(b=>{const item=rows.find(i=>i.batchId===b.id);if(item)b.remaining-=item.quantity;}); x.cart=[]; audit(x,'ORDER_CREATED');}); navigate(`/customer/orders/${id}`);};
  return <Page title="Giỏ hàng"><Table rowKey="batchId" dataSource={rows} pagination={false} columns={[{title:'Túi',render:(_,r)=>r.batch.title},{title:'Cửa hàng',render:(_,r)=>d.stores.find(s=>s.id===r.batch.storeId)?.name},{title:'Đơn giá',render:(_,r)=>money(r.batch.rescuePrice)},{title:'Số lượng',render:(_,r)=><InputNumber min={1} max={Math.min(5,r.batch.remaining)} value={r.quantity} onChange={v=>updateDemo(x=>{x.cart.find(i=>i.batchId===r.batchId).quantity=v||1;})} />},{title:'Thành tiền',render:(_,r)=>money(r.quantity*r.batch.rescuePrice)},{title:'',render:(_,r)=><Button danger icon={<DeleteOutlined />} onClick={()=>updateDemo(x=>{x.cart=x.cart.filter(i=>i.batchId!==r.batchId);})} />}]} locale={{emptyText:'Giỏ hàng đang trống'}} /><Space style={{marginTop:20}}><Text strong>Tổng: {money(total)}</Text><Button type="primary" disabled={!rows.length} onClick={checkout}>Đặt đơn</Button></Space></Page>;
}
export function DemoOrders({ store=false }) {
  const d=useDemo(); const [filter,setFilter]=useState(null); const rows=d.orders.filter(o=>(!store||o.storeId===1)&&(!filter||o.status===filter));
  return <Page title={store?'Đơn cửa hàng':'Đơn của tôi'} extra={<Select allowClear placeholder="Trạng thái" style={{width:190}} value={filter} onChange={setFilter} options={['PENDING_PAYMENT','CONFIRMED','COMPLETED','CANCELLED'].map(v=>({value:v,label:label[v]||v}))} />}><Table rowKey="id" dataSource={rows} columns={[{title:'Mã đơn',render:(_,r)=><Link to={store?`/store/orders/${r.id}`:`/customer/orders/${r.id}`}>#{r.id}</Link>},{title:'Cửa hàng',render:(_,r)=>d.stores.find(s=>s.id===r.storeId)?.name},{title:'Ngày tạo',dataIndex:'createdAt'},{title:'Tổng tiền',render:(_,r)=>money(r.total)},{title:'Trạng thái',render:(_,r)=><Status value={r.status} />},{title:'Thanh toán',render:(_,r)=><Status value={r.paymentStatus} />}]} /></Page>;
}
export function DemoOrderDetail({ store=false }) {
  const {orderId}=useParams(); const d=useDemo(); const o=d.orders.find(x=>x.id===Number(orderId)); if(!o)return <Empty description="Không tìm thấy đơn"/>;
  const pay=()=>updateDemo(x=>{const row=x.orders.find(a=>a.id===o.id);row.paymentStatus='PAID';row.status='CONFIRMED';audit(x,'PAYMENT_SUCCEEDED');});
  const cancel=()=>updateDemo(x=>{const row=x.orders.find(a=>a.id===o.id);row.status='CANCELLED';x.batches.forEach(b=>{const item=row.items.find(i=>i.batchId===b.id);if(item)b.remaining+=item.quantity;});if(row.paymentStatus==='PAID'){row.paymentStatus='REFUND_PENDING';x.refunds.push({id:Date.now(),orderId:row.id,amount:row.total,status:'PENDING'});}audit(x,'ORDER_CANCELLED');});
  return <Page title={`Đơn #${o.id}`}><Card><Descriptions bordered column={1} items={[{key:1,label:'Cửa hàng',children:d.stores.find(s=>s.id===o.storeId)?.name},{key:2,label:'Trạng thái',children:<Status value={o.status}/>},{key:3,label:'Thanh toán',children:<Status value={o.paymentStatus}/>},{key:4,label:'Mã nhận hàng',children:o.paymentStatus==='PAID'?o.pickupCode:'Có sau khi thanh toán'},{key:5,label:'Tổng tiền',children:money(o.total)}]} /><Table style={{marginTop:20}} pagination={false} rowKey="batchId" dataSource={o.items} columns={[{title:'Túi',render:(_,r)=>d.batches.find(b=>b.id===r.batchId)?.title},{title:'Số lượng',dataIndex:'quantity'},{title:'Giá',render:(_,r)=>money(r.price)}]} /><Space style={{marginTop:16}}>{!store&&o.status==='PENDING_PAYMENT'&&<Button type="primary" onClick={pay}>Mô phỏng thanh toán</Button>}{['PENDING_PAYMENT','CONFIRMED'].includes(o.status)&&<Button danger onClick={cancel}>Hủy đơn</Button>}</Space></Card></Page>;
}
export function DemoPickup() { const d=useDemo(); const [code,setCode]=useState(''); const order=d.orders.find(o=>o.pickupCode===code.trim().toUpperCase()); return <Page title="Giao hàng tại quầy"><Card><Space direction="vertical"><Text>Nhập mã nhận hàng khách cung cấp.</Text><Input.Search placeholder="Ví dụ FR101" value={code} onChange={e=>setCode(e.target.value)} style={{width:280}} />{code&&(!order?<Alert type="warning" message="Không tìm thấy đơn"/>:<><Text>Đơn #{order.id} · {money(order.total)} · <Status value={order.status}/></Text><Button type="primary" disabled={order.status!=='CONFIRMED'} onClick={()=>{updateDemo(x=>{x.orders.find(o=>o.id===order.id).status='COMPLETED';audit(x,'ORDER_COMPLETED');});message.success('Đã giao hàng');}}>Xác nhận giao</Button></>)}</Space></Card></Page>; }
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
      message.error('Chỉ nhận ảnh JPEG, PNG hoặc WebP');
      return false;
    }
    if (file.size > 500 * 1024) {
      message.error('Ảnh mẫu không được vượt quá 500 KB');
      return false;
    }
    const reader = new FileReader();
    reader.onload = () => setImageUrl(String(reader.result));
    reader.onerror = () => message.error('Không đọc được ảnh');
    reader.readAsDataURL(file);
    return false;
  }

  function save(values) {
    const name = values.name.trim();
    if (!imageUrl) { message.error('Chọn ảnh danh mục'); return; }
    if (categories.some(category => category.name.toLocaleLowerCase('vi-VN') === name.toLocaleLowerCase('vi-VN') && category.id !== editing?.id)) {
      message.error('Tên danh mục đã có trong cửa hàng');
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
    message.success('Đã lưu danh mục');
  }

  function confirmDelete(category) {
    if (data.batches.some(batch => batch.storeId === 1 && batch.categoryId === category.id)) {
      message.warning('Danh mục đang có túi giải cứu, không thể xóa');
      return;
    }
    Modal.confirm({
      title: `Xóa danh mục “${category.name}”?`,
      content: 'Danh mục sẽ bị xóa khỏi dữ liệu mẫu của cửa hàng.',
      okText: 'Xóa', okButtonProps: { danger: true }, cancelText: 'Hủy',
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
export function DemoBatches() { const d=useDemo(); const [open,setOpen]=useState(false); const [editing,setEditing]=useState(null); const [form]=Form.useForm(); const rows=d.batches.filter(b=>b.storeId===1); const show=b=>{setEditing(b||null);form.setFieldsValue(b||{categoryId:1,originalPrice:80000,rescuePrice:30000,remaining:5,pickup:'20:00–21:00'});setOpen(true);}; return <Page title="Túi giải cứu của cửa hàng" extra={<Button type="primary" icon={<PlusOutlined/>} onClick={()=>show()}>Tạo túi</Button>}><Table rowKey="id" dataSource={rows} columns={[{title:'Túi',dataIndex:'title'},{title:'Giá',render:(_,r)=>money(r.rescuePrice)},{title:'Còn',dataIndex:'remaining'},{title:'Giờ nhận',dataIndex:'pickup'},{title:'Trạng thái',render:(_,r)=><Status value={r.status}/>},{title:'Thao tác',render:(_,r)=><Space><Button icon={<EditOutlined/>} onClick={()=>show(r)}>Sửa</Button><Button danger disabled={r.status==='CANCELLED'} onClick={()=>updateDemo(x=>{x.batches.find(b=>b.id===r.id).status='CANCELLED';audit(x,'BATCH_CANCELLED');})}>Hủy</Button></Space>}]} /><Modal title={editing?'Sửa túi':'Tạo túi'} open={open} onOk={()=>form.submit()} onCancel={()=>setOpen(false)}><Form form={form} layout="vertical" onFinish={v=>{updateDemo(x=>{if(editing)Object.assign(x.batches.find(b=>b.id===editing.id),v);else x.batches.push({...v,id:Date.now(),storeId:1,status:'AVAILABLE'});audit(x,editing?'BATCH_UPDATED':'BATCH_CREATED');});setOpen(false);form.resetFields();}}><Form.Item name="title" label="Tên túi" rules={[{required:true}]}><Input/></Form.Item><Form.Item name="categoryId" label="Danh mục"><Select options={d.categories.filter(c=>c.storeId===1).map(c=>({value:c.id,label:c.name}))}/></Form.Item><Form.Item name="originalPrice" label="Giá gốc"><InputNumber min={1000} style={{width:'100%'}}/></Form.Item><Form.Item name="rescuePrice" label="Giá giải cứu"><InputNumber min={1000} style={{width:'100%'}}/></Form.Item><Form.Item name="remaining" label="Số lượng"><InputNumber min={0} style={{width:'100%'}}/></Form.Item><Form.Item name="pickup" label="Giờ nhận"><Input/></Form.Item></Form></Modal></Page>; }
export function DemoAdmin({ section }) { const d=useDemo(); const [form]=Form.useForm(); const title={dashboard:'Tổng quan',stores:'Duyệt cửa hàng',categories:'Danh mục',orders:'Đơn hàng',refunds:'Hoàn tiền',payouts:'Chi trả',finance:'Đối soát',audit:'Nhật ký'}[section]; const change=(type,id,values)=>updateDemo(x=>{Object.assign(x[type].find(r=>r.id===id),values);audit(x,`${type.toUpperCase()}_UPDATED`,'Admin mẫu');}); const columns={stores:[{title:'Cửa hàng',dataIndex:'name'},{title:'Chủ',dataIndex:'owner'},{title:'Địa chỉ',dataIndex:'address'},{title:'Trạng thái',render:(_,r)=><Status value={r.status}/>},{title:'Thao tác',render:(_,r)=><Space><Button icon={<CheckOutlined/>} disabled={r.status==='APPROVED'} onClick={()=>change('stores',r.id,{status:'APPROVED'})}>Duyệt</Button><Button danger icon={<CloseOutlined/>} onClick={()=>change('stores',r.id,{status:r.status==='LOCKED'?'APPROVED':'LOCKED'})}>{r.status==='LOCKED'?'Mở khóa':'Khóa'}</Button></Space>}],categories:[{title:'Tên danh mục',dataIndex:'name'},{title:'',render:(_,r)=><Button danger icon={<DeleteOutlined/>} onClick={()=>updateDemo(x=>{x.categories=x.categories.filter(c=>c.id!==r.id);})}>Xóa</Button>}],orders:[{title:'Mã đơn',dataIndex:'id'},{title:'Cửa hàng',render:(_,r)=>d.stores.find(s=>s.id===r.storeId)?.name},{title:'Số tiền',render:(_,r)=>money(r.total)},{title:'Trạng thái',render:(_,r)=><Status value={r.status}/>},{title:'',render:(_,r)=><Button disabled={!['CONFIRMED','PENDING_PAYMENT'].includes(r.status)} onClick={()=>change('orders',r.id,{status:'CANCELLED'})}>Hủy đơn</Button>}],refunds:[{title:'Mã hoàn',dataIndex:'id'},{title:'Đơn',dataIndex:'orderId'},{title:'Số tiền',render:(_,r)=>money(r.amount)},{title:'Trạng thái',render:(_,r)=><Status value={r.status}/>},{title:'',render:(_,r)=><Button disabled={r.status==='COMPLETED'} onClick={()=>{change('refunds',r.id,{status:'COMPLETED'});updateDemo(x=>{const o=x.orders.find(o=>o.id===r.orderId);if(o)o.paymentStatus='REFUNDED';});}}>Xác nhận hoàn</Button>}],payouts:[{title:'Kỳ',dataIndex:'period'},{title:'Cửa hàng',render:(_,r)=>d.stores.find(s=>s.id===r.storeId)?.name},{title:'Thực nhận',render:(_,r)=>money(r.amount)},{title:'Trạng thái',render:(_,r)=><Status value={r.status}/>},{title:'',render:(_,r)=><Button disabled={r.status==='PAID'} onClick={()=>change('payouts',r.id,{status:'PAID'})}>Đã chi trả</Button>}],audit:[{title:'Thời gian',dataIndex:'time'},{title:'Người thao tác',dataIndex:'actor'},{title:'Hành động',dataIndex:'action'}]};
  return <Page title={title} extra={<Button icon={<ReloadOutlined/>} onClick={()=>{Modal.confirm({title:'Đặt lại dữ liệu mẫu?',onOk:resetDemo});}}>Đặt lại dữ liệu mẫu</Button>}>{section==='dashboard'?<><Row gutter={16}>{[['Cửa hàng',d.stores.length],['Đơn hàng',d.orders.length],['Doanh thu',money(d.orders.filter(o=>o.paymentStatus==='PAID').reduce((n,o)=>n+o.total,0))],['Chờ hoàn',d.refunds.filter(r=>r.status==='PENDING').length]].map(([k,v])=><Col xs={12} md={6} key={k}><Card><Statistic title={k} value={v}/></Card></Col>)}</Row><Alert style={{marginTop:16}} type="info" message="Số liệu từ dữ liệu mẫu trong trình duyệt."/></>:section==='finance'?<Card><Descriptions column={1} bordered items={[{key:1,label:'GMV đã thanh toán',children:money(d.orders.filter(o=>o.paymentStatus==='PAID').reduce((n,o)=>n+o.total,0))},{key:2,label:'Hoàn tiền chờ xử lý',children:money(d.refunds.filter(r=>r.status==='PENDING').reduce((n,r)=>n+r.amount,0))},{key:3,label:'Chi trả chờ xác nhận',children:money(d.payouts.filter(p=>p.status==='PENDING').reduce((n,p)=>n+p.amount,0))}]} /></Card>:<>{section==='categories'&&<Form form={form} layout="inline" style={{marginBottom:16}} onFinish={v=>{updateDemo(x=>{x.categories.push({id:Date.now(),name:v.name});audit(x,'CATEGORY_CREATED','Admin mẫu');});form.resetFields();}}><Form.Item name="name" rules={[{required:true}]}><Input placeholder="Tên danh mục"/></Form.Item><Button htmlType="submit" type="primary" icon={<PlusOutlined/>}>Thêm</Button></Form>}<Table rowKey="id" dataSource={d[section]||[]} columns={columns[section]} /></>}</Page>;
}
