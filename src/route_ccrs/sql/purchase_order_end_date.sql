select
  case
    when po.objstate = 'Cancelled' or pol.objstate = 'Cancelled' then
      null
    else
      pol.planned_receipt_date
  end end_date
from ifsapp.purchase_order_line pol
join ifsapp.purchase_order po
  on pol.order_no = po.order_no
where pol.order_no = :order_no
  and pol.line_no = :line
  and pol.release_no = :release
