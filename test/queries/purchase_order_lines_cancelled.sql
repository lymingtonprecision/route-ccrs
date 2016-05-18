select
  pol.order_no,
  pol.line_no line,
  pol.release_no release
from ifsapp.purchase_order_line pol
join ifsapp.purchase_order po
  on pol.order_no = po.order_No
where pol.objstate = 'Cancelled'
   or po.objstate = 'Cancelled'
