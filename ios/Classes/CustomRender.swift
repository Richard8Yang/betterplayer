import Flutter
import OpenGLES.ES3
import GLKit

@objc public class CustomRender: NSObject, FlutterTexture {
  var fboTargetPixelBuffer: CVPixelBuffer? = nil;
  var fboTextureCache: CVOpenGLESTextureCache? = nil;
  var fboTexture: CVOpenGLESTexture? = nil;
  var fboId: GLuint = 0;
  var rboId: GLuint = 0;

  var glWidth: Double = 640;
  var glHeight: Double = 480;

  //var eAGLShareContext: EAGLContext?;
  var eglEnv: EglEnv?;
  var shareEglCtx: EAGLContext?;

  var worker: RenderWorker? = nil;

  var disposed: Bool = false;

  @objc public func initialize(_ shareContext: EAGLContext?) {
    self.shareEglCtx = shareContext;
    //self.eAGLShareContext = EAGLContext.init(api: EAGLRenderingAPI.openGLES3);
    self.setup();
  }

  func setup() {
    initEGL();
    self.worker = RenderWorker();
    self.worker!.setup();
  }
  
  func getEgl() -> Array<Int64> {
    var _egls = [Int64](repeating: 0, count: 6);
    _egls[2] = self.eglEnv!.getContext();
    return _egls;
  }

  @objc public func getTextureId() -> GLuint {
    return CVOpenGLESTextureGetName(fboTexture!);
  }

  @objc public func updateTextureSize(_ width: Double, height: Double) {
    glWidth = width;
    glHeight = height;
    glBindRenderbuffer(GLenum(GL_RENDERBUFFER), rboId);
    glRenderbufferStorage(GLenum(GL_RENDERBUFFER), GLenum(GL_DEPTH24_STENCIL8), GLsizei(glWidth), GLsizei(glHeight));
    //glBindRenderbuffer(GLenum(GL_RENDERBUFFER), 0);
    self.createCVBufferWithSize(
      size: CGSize(width: glWidth, height: glHeight),
      context: self.eglEnv!.context!
    );
    glBindTexture(CVOpenGLESTextureGetTarget(fboTexture!), CVOpenGLESTextureGetName(fboTexture!));
    //glTexImage2D(GLenum(GL_TEXTURE_2D), 0, GL_RGBA8, glWidth, glHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
    //glBindTexture(GLenum(GL_TEXTURE_2D), 0);
  }
  
  @objc public func updateTexture(_ sourceTexture: Int64) -> Bool {
    glEnable(GLenum(GL_BLEND));
    glBlendFunc(GLenum(GL_SRC_ALPHA), GLenum(GL_ONE_MINUS_SRC_ALPHA));
 
    glBindFramebuffer(GLenum(GL_FRAMEBUFFER), fboId);

    glClear(GLbitfield(GL_COLOR_BUFFER_BIT) | GLbitfield(GL_DEPTH_BUFFER_BIT) | GLbitfield(GL_STENCIL_BUFFER_BIT));

    self.worker!.renderTexture(texture: GLuint(sourceTexture), matrix: nil, isFBO: false);

    glBindFramebuffer(GLenum(GL_FRAMEBUFFER), 0);

    glFinish();

    // TODO: callback to flutter notifying texture updated
    // flutter side can do texture copy immediately to its local context
    // then use the local texture copy for further rendering
    
    return true;
  }
  
  public func copyPixelBuffer() -> Unmanaged<CVPixelBuffer>? {
    var pixelBuffer: CVPixelBuffer? = nil;
    pixelBuffer = fboTargetPixelBuffer;
    if (pixelBuffer != nil) {
      let result = Unmanaged.passRetained(pixelBuffer!);
      return result;
    } else {
      NSLog("pixelBuffer is nil.... ");
      return nil;
    }
  }
  
  // ==================================
  func initEGL() {
    self.eglEnv = EglEnv();
    self.eglEnv!.setupRender(shareContext: shareEglCtx);
    self.eglEnv!.makeCurrent();

    initOffscreenFBO();
  }

  func initOffscreenFBO() {
    let res: Bool = self.createCVBufferWithSize(
      size: CGSize(width: glWidth, height: glHeight),
      context: self.eglEnv!.context!
    );
    if (!res) { return; }
    
    checkGlError(op: "EglEnv initGL 11...")

    glBindTexture(CVOpenGLESTextureGetTarget(fboTexture!), CVOpenGLESTextureGetName(fboTexture!));

    checkGlError(op: "EglEnv initGL 2...")

    glEnable(GLenum(GL_BLEND));
    glBlendFunc(GLenum(GL_ONE), GLenum(GL_ONE_MINUS_SRC_ALPHA));
    
    glEnable(GLenum(GL_CULL_FACE));
    
    glViewport(0, 0, GLsizei(glWidth), GLsizei(glHeight));
    
    checkGlError(op: "EglEnv initGL 1...")

    glGenRenderbuffers(1, &rboId);
    glBindRenderbuffer(GLenum(GL_RENDERBUFFER), rboId);
    
    glRenderbufferStorage(GLenum(GL_RENDERBUFFER), GLenum(GL_DEPTH24_STENCIL8), GLsizei(glWidth), GLsizei(glHeight));
    
    glGenFramebuffers(1, &fboId);
    glBindFramebuffer(GLenum(GL_FRAMEBUFFER), fboId);
    glFramebufferTexture2D(GLenum(GL_FRAMEBUFFER), GLenum(GL_COLOR_ATTACHMENT0), GLenum(GL_TEXTURE_2D), CVOpenGLESTextureGetName(fboTexture!), 0);
    glFramebufferRenderbuffer(GLenum(GL_FRAMEBUFFER), GLenum(GL_DEPTH_ATTACHMENT), GLenum(GL_RENDERBUFFER), rboId);
    glFramebufferRenderbuffer(GLenum(GL_FRAMEBUFFER), GLenum(GL_STENCIL_ATTACHMENT), GLenum(GL_RENDERBUFFER), rboId);
    
    if(glCheckFramebufferStatus(GLenum(GL_FRAMEBUFFER)) != GL_FRAMEBUFFER_COMPLETE) {
      NSLog("failed to make complete framebuffer object %d", glCheckFramebufferStatus(GLenum(GL_FRAMEBUFFER)));
    }
    
    checkGlError(op: "EglEnv initGL 2...")
  }
  
  func createCVBufferWithSize(size: CGSize, context: EAGLContext) -> Bool {
    let res: CVReturn = CVOpenGLESTextureCacheCreate(kCFAllocatorDefault, nil, context, nil, &fboTextureCache);
    if (res != kCVReturnSuccess) {
      NSLog("Failed to CVOpenGLESTextureCacheCreate %d", res);
      return false;
    }
      
    let attrs = [
      kCVPixelBufferPixelFormatTypeKey: NSNumber(value: kCVPixelFormatType_32BGRA),
      kCVPixelBufferOpenGLCompatibilityKey: kCFBooleanTrue,
      kCVPixelBufferOpenGLESCompatibilityKey: kCFBooleanTrue,
      kCVPixelBufferIOSurfacePropertiesKey: [:]
    ] as CFDictionary
    
    let cv2: CVReturn = CVPixelBufferCreate(kCFAllocatorDefault, Int(size.width), Int(size.height),
                                            kCVPixelFormatType_32BGRA, attrs, &fboTargetPixelBuffer);
    if (cv2 != kCVReturnSuccess) {
      NSLog("Failed to CVPixelBufferCreate %d", cv2);
      return false;
    }
    
    let cvr: CVReturn = CVOpenGLESTextureCacheCreateTextureFromImage(kCFAllocatorDefault,
                                                                     fboTextureCache!,
                                                                     fboTargetPixelBuffer!,
                                                                     nil,
                                                                     GLenum(GL_TEXTURE_2D),
                                                                     GL_RGBA,
                                                                     GLsizei(size.width),
                                                                     GLsizei(size.height),
                                                                     GLenum(GL_BGRA),
                                                                     GLenum(GL_UNSIGNED_BYTE),
                                                                     0,
                                                                     &fboTexture);
    if (cvr != kCVReturnSuccess) {
      NSLog("Failed to CVOpenGLESTextureCacheCreateTextureFromImage %d", cvr);
      return false;
    }

    return true;
  }

  func checkGlError(op: String) {
    let error = glGetError();
    if (error != GL_NO_ERROR) {
      print("ES30_ERROR", "\(op): glError \(error)")
    }
  }

  @objc public func dispose() {
    self.disposed = true;

    //self.eAGLShareContext = nil;
    
    self.eglEnv!.dispose();
    self.eglEnv = nil;
    
    EAGLContext.setCurrent(nil);
  }
  
}

