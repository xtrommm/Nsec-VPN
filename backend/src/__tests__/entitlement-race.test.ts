jest.mock('../secrets',()=>({JWT_SECRET:require('crypto').randomBytes(32).toString('hex')}));
jest.mock('../db',()=>({prisma:{device:{findUnique:jest.fn(),updateMany:jest.fn()},adminUser:{findUnique:jest.fn()}}}));
import jwt from 'jsonwebtoken';
import {JWT_SECRET} from '../secrets';
import {prisma} from '../db';
import {authenticateDevice,authenticateAdmin} from '../middleware/auth';
function response(){const res:any={status:jest.fn(),json:jest.fn()};res.status.mockReturnValue(res);res.json.mockReturnValue(res);return res;}
beforeEach(()=>jest.clearAllMocks());
it('conditional expiry cleanup cannot overwrite a concurrently granted VIP pass',async()=>{
 const expired=new Date(Date.now()-1000);const renewed=new Date(Date.now()+86400000);
 (prisma.device.findUnique as jest.Mock).mockResolvedValueOnce({id:'owned-device',deviceId:'installation',tier:'VIP',isBlocked:false,passExpiresAt:expired}).mockResolvedValueOnce({id:'owned-device',deviceId:'installation',tier:'VIP',isBlocked:false,passExpiresAt:renewed});
 (prisma.device.updateMany as jest.Mock).mockResolvedValue({count:0});
 const req:any={headers:{authorization:'Bearer '+jwt.sign({deviceId:'installation'},JWT_SECRET)}};const next=jest.fn();await authenticateDevice(req,response(),next);
 expect(prisma.device.updateMany).toHaveBeenCalledWith({where:{id:'owned-device',tier:'VIP',passExpiresAt:expired},data:{tier:'FREE',passExpiresAt:null}});expect(req.device.tier).toBe('VIP');expect(next).toHaveBeenCalled();
});
it('admin server-side role cannot be elevated by a stale JWT role',async()=>{
 (prisma.adminUser.findUnique as jest.Mock).mockResolvedValue({role:'ADMIN'});const res=response();const next=jest.fn();await authenticateAdmin('SUPER_ADMIN')({headers:{authorization:'Bearer '+jwt.sign({adminId:'admin-test',role:'SUPER_ADMIN'},JWT_SECRET)}} as any,res,next);
 expect(res.status).toHaveBeenCalledWith(403);expect(next).not.toHaveBeenCalled();
});
