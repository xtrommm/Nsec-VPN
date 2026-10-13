jest.mock('../db',()=>({prisma:{device:{count:jest.fn()},peerSession:{count:jest.fn()},serverNode:{findMany:jest.fn()},anonymousStat:{groupBy:jest.fn()}}}));
import {prisma} from '../db';
import {StatsService} from '../services/stats';
it('ten concurrent dashboard calls share exactly five aggregate database queries',async()=>{
 (prisma.device.count as jest.Mock).mockResolvedValue(10);(prisma.peerSession.count as jest.Mock).mockResolvedValue(1);
 (prisma.serverNode.findMany as jest.Mock).mockResolvedValue([{id:'fixture-node',name:'Test',active:true,currentLoad:20}]);
 (prisma.anonymousStat.groupBy as jest.Mock).mockResolvedValue([]);
 const results=await Promise.all(Array.from({length:10},()=>StatsService.getDashboardMetrics()));
 expect(results.every(x=>x.totalDevices===10&&x.avgServerLoad===20)).toBe(true);
 expect(prisma.device.count).toHaveBeenCalledTimes(2);expect(prisma.peerSession.count).toHaveBeenCalledTimes(1);
 expect(prisma.serverNode.findMany).toHaveBeenCalledTimes(1);expect(prisma.anonymousStat.groupBy).toHaveBeenCalledTimes(1);
});
