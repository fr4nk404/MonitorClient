package com.okami.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.okami.bean.ConfigBean;
import com.okami.bean.GlobaVariableBean;
import com.okami.config.DBConfig;
import com.okami.dao.impl.FileIndexDao;
import com.okami.entities.FileIndex;
import com.okami.entities.MonitorTask;
import com.okami.util.DataUtil;
import com.okami.util.FileUtil;
import net.contentobjects.jnotify.*;  

/**
 * 监控模块
 * @author orleven
 * @date 2016年12月31日
 */
@Component
@Scope("prototype")
public class MonitorThread {  
    
    // 通用配置
    private MonitorTask monitorTask;
    private FileIndexDao fileIndexDao ;
    
    // 队列
    private Queue<String> qHeartBeats;
    private Queue<String> qMonitor;
    private Queue<String> qRepaire;
    
    // 线程属性配置
    private String name;
    private Listener listener;
    private int watchID;
    private int state;
    


    public boolean init(MonitorTask monitorTask,FileIndexDao fileIndexDao){
        this.monitorTask = monitorTask;
        this.fileIndexDao = fileIndexDao;
        this.listener = new Listener(monitorTask,fileIndexDao);
        this.state = 1;
       
        return true;
    }
    
    public void setQqueue(Queue<String> qHeartBeats,Queue<String> qMonitor,Queue<String> qRepaire){
        this.qHeartBeats = qHeartBeats;
        this.qMonitor = qMonitor;
        this.qRepaire = qRepaire;
        this.listener.setQqueue(qHeartBeats,qMonitor,qRepaire);
    }
    
        
    public void setQHeartBeats(Queue<String> qHeartBeats){
        this.qHeartBeats = qHeartBeats;
        this.listener.setQHeartBeats(qHeartBeats);
    }
    
    
    public Queue<String> getQMonitor(){
        return qMonitor;
    }
    
    public void setQMonitor(Queue<String> qMonitor){
        this.qMonitor = qMonitor;
        this.listener.setQMonitor(qMonitor);
    }
    
    public void setQRepaire(Queue<String> qRepaire){
        this.qRepaire = qRepaire;
        this.listener.setQRepaire(qRepaire);
    }
    
    
    public int  getState(){
        return state;
    }
    
    public String  getName(){
        return this.name;
    }
    
    public void  setName(String name){
        this.name = name;
    }

    public boolean start() {  
        int mask = JNotify.FILE_CREATED | JNotify.FILE_DELETED | JNotify.FILE_MODIFIED | JNotify.FILE_RENAMED;  
        boolean watchSubtree = true;  
        try {  
            // 防篡改模式（安全模式）
            if(monitorTask.getRunMode()==2){ 
            	qHeartBeats.offer(DataUtil.getTime()+"\tInfo\tStart Monitor(Safe Mode): " + monitorTask.getMonitorPath());
            	IOC.log.warn("Start Monitor(Safe Mode): " + monitorTask.getMonitorPath());
            	qHeartBeats.offer(DataUtil.getTime()+"\tInfo\tTurn To Temp Mode: " + monitorTask.getMonitorPath());
                IOC.log.warn("Turn To Temp Mode: " + monitorTask.getMonitorPath());
                this.listener.setMode("Temp");
            }
            // 人工模式
            else if(monitorTask.getRunMode()==1){
                this.listener.setMode("Human");
                qHeartBeats.offer(DataUtil.getTime()+"\tInfo\tStart Monitor(Human Mode): " + monitorTask.getMonitorPath());
                IOC.log.warn("Start Monitor(Human Mode): " + monitorTask.getMonitorPath());
            }
            
            watchID = JNotify.addWatch(monitorTask.getMonitorPath(), mask, watchSubtree, this.listener);  
            this.state = 1;
            return true;
        } catch (Exception e) {  
        	IOC.log.error(e.getMessage());
        	return false;
        }  
    }  
    
    public void stop() {  
        try {
            boolean res = JNotify.removeWatch(watchID);
            this.state = 0;
            if (!res) {  
                // invalid  
            } 

        } catch (JNotifyException e) {
        	IOC.log.error(e.getMessage());
        }  
        
    }
    

    /**
     * 监控核心
     * @author orleven
     * @date 2017年1月5日
     */
    public class Listener implements JNotifyListener {  
  
        private FileIndexDao fileIndexDao;
        private MonitorTask monitorTask;
        private String[] whiteList;
        private String[] blackList;
        private String mode;
        private Queue<String> qHeartBeats;
        private Queue<String> qMonitor;
        private Queue<String> qRepaire;

        
        public Listener(MonitorTask monitorTask,FileIndexDao fileIndexDao){
            this.monitorTask = monitorTask;
            this.fileIndexDao = fileIndexDao;
            String whiteStr = monitorTask.getWhiteList();
            String blackStr = monitorTask.getBlackList();
                        
            
            if(whiteStr!=null){
                this.whiteList = whiteStr.split(",");
            }else{
                this.whiteList = null;
            }
            if(blackStr!=null){
                this.blackList = blackStr.split(",");
            }else{
                this.blackList = null;
            }
        
        }
        
        public void setQHeartBeats(Queue<String> qHeartBeats){
            this.qHeartBeats = qHeartBeats;
        }
        
        public void setQMonitor(Queue<String> qMonitor){
            this.qMonitor = qMonitor;
        }
        
        public void setQRepaire(Queue<String> qRepaire){
            this.qRepaire = qRepaire;
        }
        
        public void setQqueue(Queue<String> qHeartBeats,Queue<String> qMonitor,Queue<String> qRepaire){
            this.qHeartBeats = qHeartBeats;
            this.qMonitor = qMonitor;
            this.qRepaire = qRepaire;
        }
        
        public void setMode(String mode){
            this.mode = mode;
        }
        
    
        public void fileRenamed(int wd, String rootPath, String oldName, String newName) {  
            String time = DataUtil.getTime();
            modeDeal("Created",rootPath,newName,time);
            modeDeal("Deleted",rootPath,oldName,time);
        }  
  
        public void fileModified(int wd, String rootPath, String name) {    
            String time = DataUtil.getTime();
            modeDeal("Modified",rootPath,name,time);  
        }  
  
        public void fileDeleted(int wd, String rootPath, String name) {  
            String time = DataUtil.getTime();
            modeDeal("Deleted",rootPath,name,time);
        }  
  
        public void fileCreated(int wd, String rootPath, String name) {  
            String time = DataUtil.getTime();
            modeDeal("Created",rootPath,name,time);
        }  
  
        /**
         * 统一处理
         * @param action
         * @param path
         * @param name
         * @param time
         */
        public void modeDeal(String action,String path,String name,String time){
            
        	String filename = path+ File.separator + name;

            switch(mode){
            case "Human":
                qHeartBeats.offer(time+"\t"+action+"\t"+filename);
                IOC.log.warn(action+": "+filename);
                break;
            case "Safe":
                checkNameList(time,action,filename);
                break;
            case "Temp":
                if(qMonitor.isEmpty()){
                    // 备份时发生文件操作，停止备份、监控、还原线程
                    if(monitorTask.getBCMode()==0){
                        IOC.log.warn(action+": "+filename);
                        qHeartBeats.offer(time+"\t"+action+"\t"+filename);
                        monitorTask.setRunMode(0);
                        stop();
                        qHeartBeats.offer(DataUtil.getTime()+"\tInfo\tDon't operate files when backing up ! Stop Monitor: " + monitorTask.getMonitorPath());
                    	IOC.log.warn("Don't operate files when backing up ! Stop Monitor: " + monitorTask.getMonitorPath());
                    	GlobaVariableBean globaVariableBean = IOC.instance().getClassobj(GlobaVariableBean.class);
                    	for(int i=0;i<globaVariableBean.getMonitorTaskList().size();i++){
                    		if(globaVariableBean.getMonitorTaskList().get(i).getTaskName().equals(monitorTask.getTaskName())){
                    			try {
                    				monitorTask.setStatus(0);
                    				globaVariableBean.getMonitorTaskDao().updateTask(monitorTask);
									globaVariableBean.getFileIndexDaoList().get(i).closeConnection();
	                    			globaVariableBean.getQMonitorList().remove(i);
	            					globaVariableBean.getMonitorThreadList().remove(i);
	            					globaVariableBean.getBackupAndCheckThreadList().remove(i);
	            					globaVariableBean.getFileIndexDaoList().remove(i);
	            					globaVariableBean.getMonitorTaskList().remove(i);
	            					i--;
								} catch (Exception e) {
									IOC.log.error(e.getMessage());
								}

                    		}
                    	}
                    }
                    
                    // 自检
                    else{
                        checkNameList(time,action,filename);
                    }
                }else{
                    if(qMonitor.poll() == "True"){
                        
                        mode = "Safe";
                    }
                    checkNameList(time,action,filename);
                }
                break;
            default:
                break;
            }
        }
       
        /**
         * 判断是否属于黑白名单
         * @param time
         * @param action
         * @param path
         * @param name
         */
        public void checkNameList(String time,String action,String filename){
            // 安全模式

            if(mode.equals("Safe")){
                boolean whiteFlag = false;
                boolean blackFlag = false;
                String parentPath = filename.substring(0,filename.lastIndexOf(File.separator));
                for(int i=0;i<whiteList.length;i++){
                	if(parentPath.indexOf(whiteList[i]) == 0){  // 不应该大于0
                        // 白名单
                        whiteFlag = true;
                        String name = filename.substring(filename.lastIndexOf(File.separator));
                        if(name.indexOf(".")>=0){
                            String suffix = name.substring(name.indexOf(".")+1).toLowerCase();
                            for(int j=0;j<blackList.length;j++){
                                // 黑名单
                                if(suffix.indexOf(blackList[j].toLowerCase())>=0){
                                    blackFlag = true;
                                    repaire(time,action,filename );
                                    break;
                                }
                            }
                        }

                        // 不在黑名单，即白名单里面
                        if(!blackFlag||whiteFlag){
                            qHeartBeats.offer(time+"\t"+action+"\t"+filename);
                            IOC.log.warn(action + ": " + filename);
                        }
                        break;
                    }
                }
                
                // 不在白名单
                if(!whiteFlag){
                  repaire(time,action,filename);
                }
            }
            
            // 自检模式
            else{
              repaire(time,action,filename);
            }
        }
        
        /**
         * 修复函数
         */
        public void repaire(String time,String action,String filename) {
        	
    	    List<FileIndex> fileIndexList = new ArrayList<FileIndex>();
        	try {
        		// 默认选择第一个，以后如果有版本区别的话再根据版本查询
        		String indexPath = filename.substring(monitorTask.getMonitorPath().length());  // 数据库中的path
        		fileIndexList = fileIndexDao.queryIndexByPath(indexPath);
        		FileIndex fileIndex = fileIndexList.size()>0? fileIndexList.get(0):null;
				
                // 检测到文件被创建
                if(action.equals("Created")){
                	
                	// 如果flag文件中没有该文件，则进行删除
                	if(fileIndex==null){
	                    
                		qHeartBeats.offer(time+"\t"+action+"\t"+filename);
	                    IOC.log.warn(action + ": " + filename);
						FileUtil.deleteAll(new File(filename));
						qHeartBeats.offer(time+"\t"+action+"-Machine\t"+filename+" Deal Success!");
						IOC.log.warn(action + "-Machine: " + filename+" Deal Success!");
	                }
                	
	                // flag文件中存在，则进行md5校验
	                else{
	                	if(fileIndex.getType().equals("File")){
	         
	                		String sha11 = DataUtil.getSHA1ByFile(new File(filename));
	             
	                		String sha12 = fileIndex.getSha1();
	                		if(!sha12.equals( sha11)){
	                			qHeartBeats.offer(time+"\t"+action+"\t"+filename);
	                			IOC.log.warn(action + ": " + filename);
	                			FileUtil.deleteAll(new File(filename));
	                			qRepaire.offer("Restore\t"+action+"\t"+monitorTask.getMonitorPath()+"\t"+indexPath+"\t"+monitorTask.getTaskName());
							}
		                }
	                }
                }
				
                
	            else if(action.equals("Deleted")){
	            	// 如果flag文件中有该文件，则进行还原
		            if(fileIndex!=null){
		            	qHeartBeats.offer(time+"\t"+action+"\t"+filename);
		            	IOC.log.warn(action + ": " + filename);
		            	qRepaire.offer("Restore\t"+action+"\t"+monitorTask.getMonitorPath()+"\t"+indexPath+"\t"+monitorTask.getTaskName());
		            }
	            }
                
	            else if(action.equals("Modified")){
	            	// 如果flag文件中有该文件，则进行还原
	            	if(fileIndex!=null){
	                	if(fileIndex.getType().equals("File")){
	                		String sha11 = DataUtil.getSHA1ByFile(new File(filename));
	                		String sha12 = fileIndex.getSha1();
	                		if(!sha12.equals( sha11)){
	                			qHeartBeats.offer(time+"\t"+action+"\t"+ filename);
	                			IOC.log.warn(action + ": " + filename);
	                			FileUtil.deleteAll(new File(filename));
						        qRepaire.offer("Restore\t"+action+"\t"+monitorTask.getMonitorPath()+"\t"+indexPath+"\t"+monitorTask.getTaskName());
							}
		                }
	            	}
	            }
                
        	} catch (Exception e) {
        	
        		e.printStackTrace();
        		IOC.log.error(e.getMessage());

        	}

//              
//              // 如果flag文件中没有该文件，则进行删除
//              if(key==null){
//                  qHeartBeats.offer(time+"\t"+action+"\t"+filename);
//                  qRepaire.offer("Delete\t"+action+"\t"+filename);
//              }
//              
//              // flag文件中存在，则进行md5校验
//              else{
//                  String[] values = key.split("\\|");
//                  File webFile = new File(filename);
//                  File bakFile = new File(values[values.length-1]);
//                  if(webFile.isFile()){
//                      try {
//                          String md51 = DataUtil.getMd5ByFile(webFile);
//                          String md52 = values[1];
//                          String md53 = bakFile.getName();
//                          String md54 = DataUtil.getMD5(ZLibUtils.decompress(Files.readAllBytes(Paths.get(values[values.length-1]))));
//                          if(md52.equals( md53)&&md52.equals( md54)){
//                              if(!md51.equals(md52)){
//                                  qHeartBeats.offer(time+"\t"+action+"\t"+filename);
//                                  qRepaire.offer("Restore\t"+action+"\t"+filename);
//                              }
//                          }else{      
//                              System.out.println("文件出现异常！329");
//                          }
//                      } catch (IOException e) {
//                          e.printStackTrace();
//                      }
//                  }else if(webFile.isDirectory()){
//                          String md51 = DataUtil.getMD5(Paths.get(filename).getFileName().toString());
//                      String md52 = values[1];
//                      String md53 = bakFile.getName();
//                      if(md52.equals( md53)){
//                          if(!md51.equals(md52)){
//                              qHeartBeats.offer(time+"\t"+action+"\t"+filename);
//                              System.out.println("md51:"+md51);
//                              System.out.println("md52:"+md52);
//                              System.out.println("md53:"+md53);
//                              qRepaire.offer("Restore\t"+action+"\t"+filename);
//                          }
//                      }else{
//                          System.out.println("文件出现异常！343");
//                      }
//                  }
//              }
//          }
//          else if(action.equals("Deleted")){
//              // 如果flag文件中有该文件，则进行还原
//              if(key!=null){
//                  qHeartBeats.offer(time+"\t"+action+"\t"+filename);
//                  qRepaire.offer("Restore\t"+action+"\t"+filename);
//              }
//          }
//          
//          else if(action.equals("Modified")){
//              // 如果flag文件中有该文件，则进行还原
//              if(key!=null){
//                  String[] values = key.split("\\|");
//                  File webFile = new File(filename);
//                  File bakFile = new File(values[values.length-1]);
//                  if(webFile.isFile()){
//                      try {
//                          String md51 = DataUtil.getMd5ByFile(webFile);
//                          String md52 = values[1];
//                          String md53 = bakFile.getName();
//                          String md54 = DataUtil.getMD5(ZLibUtils.decompress(Files.readAllBytes(Paths.get(values[values.length-1]))));
//                          
//                          if(md52.equals( md53)&&md52.equals( md54)){
//                              if(!md51.equals(md52)){
//                                  qHeartBeats.offer(time+"\t"+action+"\t"+filename);
//                                  qRepaire.offer("Restore\t"+action+"\t"+filename);
//                              }
//                          }else{
//                              System.out.println("文件出现异常！374");
//                          }
//                      } catch (IOException e) {
//                          e.printStackTrace();
//                      }
//                  }else if(webFile.isDirectory()){
//                          String md51 = DataUtil.getMD5(Paths.get(filename).getFileName().toString());
//                      String md52 = values[1];
//                      String md53 = bakFile.getName();
//                      if(md52.equals( md53)){
//                          if(!md51.equals(md52)){
//                              qHeartBeats.offer(time+"\t"+action+"\t"+filename);
//                              qRepaire.offer("Restore\t"+action+"\t"+filename);
//                          }
//                      }else{
//                          System.out.println("文件出现异常！388");
//                      }
//                  }
//              }
        }
    }  
}  