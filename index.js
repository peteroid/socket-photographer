var express = require('express');
var app = express()
var http = require('http').Server(app);
var io = require('socket.io')(http);
var multer = require('multer');

var storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, __dirname + '/static/uploads')
  },
  filename: function (req, file, cb) {
    cb(null, String(shootCount) + '_' + file.originalname.replace('image_', ''))
  }
})

var upload = multer({
  storage: storage
})

const port = 8080

app.use(express.static('static'))

app.post('/upload', upload.single('image'), function (req, res, next) {
  console.log('file: %s', JSON.stringify(req.file));
  if (req.file) {
    res.json({'response':"Saved"});
    io.emit('img', {
      path: '/uploads/' + req.file.filename
    })
  } else {
    res.json({'response':"No file"});
  }
  //           res.json({'response':"Error"});
});

app.get('/', function(req, res){
  res.sendFile(__dirname + '/static/index.html');
});

var availableDevices = []
var shootCount = 0
io.on('connection', function(socket){
  console.log('a device connected');

  socket.on('disconnect', function(){
    console.log('a device disconnected. removing it');
    var index = -1
    for (var i = availableDevices.length - 1; i >= 0; i--) {
      if (availableDevices[i].io == socket) {
        index = i
        break
      }
    }

    if (index != -1) {
      availableDevices.splice(index, 1)
    } else {
      console.log('Not found. Skip removing.')
    }
    showAllDevices()
  });

  socket.on('device', function(device){
    console.log('Device %s is online', device);
    availableDevices.push({
      io: socket,
      name: device
    })

    showAllDevices()
  });

  socket.on('shoot', e => {
    console.log('server got shoot: %s', JSON.stringify(e))
    var timestamp = Date.now()
    shootCount++
    io.emit('shoot', {
      timestamp: timestamp,
      delay: e.delay
    })
  }) 

});

http.listen(port, function(){
  console.log('listening on *:' + port);
});

var showAllDevices = function () {
  var names = availableDevices.map(_ => _.name)
  console.log('%d device(s): %s', names.length, names)
}